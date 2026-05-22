#!/usr/bin/env sh
set -e
PRG="$0"; while [ -h "$PRG" ]; do PRG="$(readlink "$PRG")"; done
APP_HOME="$(cd "$(dirname "$PRG")" && pwd)"
exec java -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
  org.gradle.wrapper.GradleWrapperMain "$@"
