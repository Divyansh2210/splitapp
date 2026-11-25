#!/bin/sh
set -eu

# Source this file from the repo root to make the local Gradle/JDK setup available in your shell.

project_root="${PWD}"

GRADLE_USER_HOME="$project_root/.gradle-user"
export GRADLE_USER_HOME

if [ -z "${JAVA_HOME:-}" ] && [ -x "/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin/java" ]; then
    JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    export JAVA_HOME
fi

gradle_bin="$(find "$HOME/.gradle/wrapper/dists/gradle-8.7-bin" -path '*/gradle-8.7/bin/gradle' 2>/dev/null | head -n 1)"
if [ -n "$gradle_bin" ]; then
    GRADLE_HOME="${gradle_bin%/bin/gradle}"
    export GRADLE_HOME
    case ":$PATH:" in
        *":$GRADLE_HOME/bin:"*) ;;
        *) PATH="$GRADLE_HOME/bin:$PATH" ;;
    esac
    export PATH
fi
