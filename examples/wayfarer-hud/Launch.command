#!/bin/zsh
set -eu
cd "${0:A:h}/../.."
exec ./gradlew :pane-demo:runWayfarerHud --no-daemon --max-workers=2
