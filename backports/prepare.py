"""Materialize exact beta.4 sources plus the ordered patches for one Minecraft client."""
import json
from pathlib import Path
import shutil
import subprocess
import sys

root = Path(__file__).resolve().parent
target, output = sys.argv[1:]
source = root.parent / "pane/src"
dest = Path(output).resolve()
if target not in json.loads((root / "targets.json").read_text()) or dest != root / "build" / target / "generated/port":
    sys.exit("Unexpected generated source directory")
if dest.exists():
    shutil.rmtree(dest)
for area in ("ui", "mixin"):
    folder = Path("dev/pane") / area
    shutil.copytree(source / "main/java" / folder, dest / "main" / folder)
shutil.copytree(source / "test/java", dest / "test")
shutil.copytree(root / "smoke/java", dest / "smoke")
for layer in json.loads((root / "targets.json").read_text())[target]["patches"]:
    with (root / "patches" / (layer + ".patch")).open("rb") as changes:
        subprocess.run(["patch", "-p1", "-F0", "-t"], cwd=dest, stdin=changes, check=True)
