"""Owned process-tree cleanup tests. Fake wrappers only; no Gradle or Minecraft."""
import contextlib
import io
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_release_consumer as consumer


@unittest.skipIf(os.name == 'nt', 'POSIX process-group integration; Windows path is mocked separately')
class OwnedProcessTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='panelib-owned-process-test-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.log = self.root / 'consumer.log'
        self.child_pid = self.root / 'child.pid'
        self.parent_pid = self.root / 'parent.pid'

    def wrapper(self, body):
        path = self.root / 'gradlew'
        path.write_text('#!' + sys.executable + '\n' + body, encoding='utf-8')
        path.chmod(0o755)

    def process_wrapper(self):
        # The grandchild ignores TERM, proving the group KILL path is needed even when the
        # direct wrapper has already exited. The separate sentinel must remain untouched.
        child = "import os,signal,time; signal.signal(signal.SIGTERM,signal.SIG_IGN); " \
                "open('child.pid','w').write(str(os.getpid())); time.sleep(60)"
        self.wrapper('import os, subprocess, sys, time\n'
                     "open('parent.pid','w').write(str(os.getpid()))\n"
                     f'subprocess.Popen([sys.executable, "-c", {child!r}])\n'
                     'time.sleep(60)\n')

    def wait_for_child(self):
        deadline = time.monotonic() + 3
        while not self.child_pid.exists() and time.monotonic() < deadline:
            time.sleep(.01)
        self.assertTrue(self.child_pid.exists(), 'synthetic child must start before cancellation')

    @staticmethod
    def alive(pid):
        # A killed orphan may briefly remain a zombie while the OS reaps it; it cannot execute.
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            return False
        if sys.platform.startswith('linux'):
            try:
                state = Path(f'/proc/{pid}/stat').read_text().rsplit(')', 1)[1].split()[0]
                return state != 'Z'
            except FileNotFoundError:
                return False
        return True

    def assert_stopped(self):
        for file in (self.parent_pid, self.child_pid):
            self.assertTrue(file.exists())
            pid = int(file.read_text())
            deadline = time.monotonic() + 3
            while self.alive(pid) and time.monotonic() < deadline:
                time.sleep(.02)
            self.assertFalse(self.alive(pid), f'owned process {pid} still running')

    def sentinel(self):
        process = subprocess.Popen([sys.executable, '-c', 'import time; time.sleep(60)'],
                                   start_new_session=True)
        def clean():
            if process.poll() is None:
                process.terminate()
            process.wait(timeout=3)
        self.addCleanup(clean)
        return process

    def emergency_cleanup(self):
        # Test failure must not itself leak the deliberately uncooperative test process.
        if self.parent_pid.exists():
            try:
                os.killpg(int(self.parent_pid.read_text()), signal.SIGKILL)
            except ProcessLookupError:
                pass

    def test_timeout_stops_wrapper_and_term_ignoring_child_without_touching_sentinel(self):
        self.process_wrapper()
        self.addCleanup(self.emergency_cleanup)
        sentinel = self.sentinel()
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaises(subprocess.TimeoutExpired):
            consumer.run(self.root, ['build'], self.log, timeout=.35, shutdown_timeout=.15)
        self.assert_stopped()
        self.assertIsNone(sentinel.poll())

    def test_keyboard_interrupt_cleans_whole_owned_group(self):
        self.process_wrapper()
        self.addCleanup(self.emergency_cleanup)
        sentinel = self.sentinel()
        original_wait = subprocess.Popen.wait
        interrupted = False
        def interrupt_after_child(process, timeout=None):
            nonlocal interrupted
            if not interrupted:
                interrupted = True
                self.wait_for_child()
                raise KeyboardInterrupt()
            return original_wait(process, timeout=timeout)
        with patch.object(subprocess.Popen, 'wait', interrupt_after_child), \
                contextlib.redirect_stdout(io.StringIO()), self.assertRaises(KeyboardInterrupt):
            consumer.run(self.root, ['build'], self.log, shutdown_timeout=.15)
        self.assert_stopped()
        self.assertIsNone(sentinel.poll())

    def test_smoke_rejects_complete_fail_even_after_pass_and_zero_exit(self):
        self.wrapper("print('[ConsumerTest] complete PASS')\nprint('[ConsumerTest] complete FAIL checks=1')\n")
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(RuntimeError, 'smoke did not pass'):
            consumer.run(self.root, ['runClient', '-PsmokeTest'], self.log, timeout=3)

    def test_clean_smoke_pass_and_normal_failure_exit(self):
        self.wrapper("print('[ConsumerTest] complete PASS checks=5')\n")
        with contextlib.redirect_stdout(io.StringIO()):
            consumer.run(self.root, ['runClient', '-PsmokeTest'], self.log, timeout=3)
        self.wrapper('raise SystemExit(4)\n')
        with contextlib.redirect_stdout(io.StringIO()), self.assertRaisesRegex(RuntimeError, 'exit 4'):
            consumer.run(self.root, ['build'], self.log, timeout=3, shutdown_timeout=.15)


class WindowsCleanupContractTests(unittest.TestCase):
    def test_timeout_targets_only_owned_pid_tree_and_uses_new_group(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            process = Mock(pid=8675309)
            process.wait.side_effect = [subprocess.TimeoutExpired('fake-wrapper', 1), 0]
            process.poll.return_value = 0
            with patch.object(consumer.os, 'name', 'nt'), \
                    patch.object(consumer.subprocess, 'Popen', return_value=process) as start, \
                    patch.object(consumer.subprocess, 'run', return_value=Mock(returncode=0)) as stop, \
                    contextlib.redirect_stdout(io.StringIO()), self.assertRaises(subprocess.TimeoutExpired):
                consumer.run(root, ['build'], root / 'consumer.log', timeout=1, shutdown_timeout=.1)
            self.assertEqual(start.call_args.kwargs['creationflags'], 0x200)
            self.assertEqual(stop.call_args.args[0], ['taskkill', '/PID', '8675309', '/T', '/F'])


if __name__ == '__main__':
    unittest.main()
