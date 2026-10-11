@echo off
call build.bat
if errorlevel 1 exit /b %ERRORLEVEL%

echo ===================================================
echo   Running Image Segmentation regression tests
echo ===================================================
javac -cp bin -d bin src\test\java\com\segmentation\ThreadConfigurationTests.java
if errorlevel 1 exit /b %ERRORLEVEL%
java -Djava.awt.headless=true -cp bin com.segmentation.ThreadConfigurationTests
exit /b %ERRORLEVEL%
