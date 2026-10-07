@echo off
echo ===================================================
echo   Building Image Segmentation Project (Java 17)
echo ===================================================

if not exist bin mkdir bin

REM Compile all java source files
dir /s /b src\main\java\*.java > sources.txt
javac -d bin @sources.txt
del sources.txt

if %ERRORLEVEL% equ 0 (
    echo [SUCCESS] Compilation finished successfully. Output in bin/
) else (
    echo [ERROR] Compilation failed.
)
