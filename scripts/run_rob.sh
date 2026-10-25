#!/bin/bash
# Direct-JUnitCore Robolectric runner (no Gradle daemon) — the fast path
# proven in the v1.6.17/v1.6.18 rounds. Usage: run_rob.sh <TestClass> [testMethod]
cd /home/z/my-project/callmony/composeApp
CP="$(cat ../build/fullcp.txt)"
java -Xmx1280m -Dfile.encoding=UTF-8 \
  -cp "$CP" \
  org.junit.runner.JUnitCore "com.callmony.$1" 2>&1
