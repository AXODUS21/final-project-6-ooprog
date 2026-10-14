#!/usr/bin/env bash
# PlanO - Event Management System Runner Script
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "============================================================"
echo "Compiling PlanO Event Management System (Group 6)..."
echo "============================================================"

mkdir -p bin

# Find all java source files and compile
find src -name "*.java" > sources.txt
javac -d bin @sources.txt
rm -f sources.txt

echo "Compilation successful!"
echo "Launching PlanO..."
echo "============================================================"
echo ""

java -cp bin plano.Main "$@"
