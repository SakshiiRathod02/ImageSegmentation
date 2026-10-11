@echo off
echo ===================================================
echo   Building Image Segmentation Project (Java 17)
echo ===================================================

if not exist bin mkdir bin

REM Compile all java source files
dir /s /b src\main\java\*.java > sources.txt
javac -d bin @sources.txt
set BUILD_RESULT=%ERRORLEVEL%
del sources.txt

if %BUILD_RESULT% equ 0 (
    echo [SUCCESS] Compilation finished successfully. Output in bin/
) else (
    echo [ERROR] Compilation failed.
    exit /b %BUILD_RESULT%
)
