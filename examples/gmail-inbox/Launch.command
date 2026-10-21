#!/bin/zsh
set -eu
cd "${0:A:h}/../.."
exec ./gradlew :pane-demo:runMail --no-daemon --max-workers=2
