#!/usr/bin/env bash
# Menjalankan unit test lapisan core/ (pure Kotlin, tanpa Android) memakai kotlinc + stub JUnit mini.
# Pemakaian: KOTLINC=/path/ke/kotlinc tools/run_core_tests.sh
set -euo pipefail
cd "$(dirname "$0")/.."
KOTLINC="${KOTLINC:-kotlinc}"
OUT="$(mktemp -d)"
"$KOTLINC" app/src/main/java/com/tracelens/app/core/*.kt \
  app/src/test/java/com/tracelens/app/core/CoreTest.kt \
  tools/coretest_stub/*.kt -include-runtime -d "$OUT/coretest.jar" 2>&1 | grep -v '^warning' || true
java -cp "$OUT/coretest.jar" RunnerKt
