@echo off
rem PlanO - Event Management System runner (Windows Command Prompt or PowerShell)
setlocal
cd /d "%~dp0"

echo ============================================================
echo Compiling PlanO Event Management System (Group 6)...
echo ============================================================

if not exist bin mkdir bin

rem PlanO targets Java 8. On JDK 9 or newer, --release 8 makes the compiler
rem reject any API that Java 8 does not have, so the project stays Java 8 compatible.
set "RELEASE_FLAG="
javac --release 8 -version >nul 2>&1 && set "RELEASE_FLAG=--release 8"

rem Compiling Main pulls in every class it uses through the source path.
javac -encoding UTF-8 %RELEASE_FLAG% -d bin -sourcepath src src\plano\Main.java
if errorlevel 1 (
    echo Compilation failed.
    exit /b 1
)

echo Compilation successful!
echo Launching PlanO...
echo ============================================================
echo.

java -cp bin plano.Main %*
