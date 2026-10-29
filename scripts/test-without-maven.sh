#!/usr/bin/env bash
# Compiles the scheduler and its tests with plain javac and runs them with the JUnit Platform
# Console Launcher. Use this when Maven cannot download dependencies.
#
# Usage: JUNIT_CONSOLE_JAR=/path/to/junit-platform-console-standalone.jar scripts/test-without-maven.sh
# (Debian/Ubuntu: apt install junit5 -> /usr/share/java/junit-platform-console-standalone.jar)
set -euo pipefail
cd "$(dirname "$0")/.."

: "${JUNIT_CONSOLE_JAR:?Set JUNIT_CONSOLE_JAR to junit-platform-console-standalone.jar}"
rm -rf out && mkdir -p out/classes out/test-classes

javac -Xlint:all -d out/classes src/main/java/com/complyance/scheduler/*.java
javac -Xlint:all -cp "out/classes:${JUNIT_CONSOLE_JAR}" -d out/test-classes src/test/java/com/complyance/scheduler/*.java
java -jar "${JUNIT_CONSOLE_JAR}" --class-path out/classes:out/test-classes \
     --scan-class-path out/test-classes --disable-banner --details=summary
