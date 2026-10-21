#!/usr/bin/env bash
# PlanO - Event Management System runner (macOS, Linux, or Git Bash on Windows)
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "============================================================"
echo "Compiling PlanO Event Management System (Group 6)..."
echo "============================================================"

mkdir -p bin

# PlanO targets Java 8. On JDK 9 or newer, --release 8 makes the compiler
# reject any API that Java 8 does not have, so the project stays Java 8 compatible.
RELEASE_FLAG=""
if javac --release 8 -version > /dev/null 2>&1; then
    RELEASE_FLAG="--release 8"
fi

# Compiling Main pulls in every class it uses through the source path.
javac -encoding UTF-8 $RELEASE_FLAG -d bin -sourcepath src src/plano/Main.java

echo "Compilation successful!"
echo "Launching PlanO..."
echo "============================================================"
echo ""

java -cp bin plano.Main "$@"
