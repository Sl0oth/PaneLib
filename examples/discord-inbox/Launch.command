#!/bin/zsh
set -eu
cd "${0:A:h}/../.."
exec ./gradlew :pane-demo:runGather --no-daemon --max-workers=2
