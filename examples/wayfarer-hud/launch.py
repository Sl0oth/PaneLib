#!/usr/bin/env python3
"""Compatibility entry point. No arguments launches the public Gradle example."""
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / 'tools' / 'fixtures'))
from runner import compatibility_main

if __name__ == '__main__':
    raise SystemExit(compatibility_main('wayfarer-hud'))
