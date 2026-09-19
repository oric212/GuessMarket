@echo off
setlocal EnableExtensions
cd /d "%~dp0"
if not defined JAVAFX_SDK (echo Set JAVAFX_SDK to the JavaFX 25 SDK directory.& exit /b 1)
if defined JAVA_HOME (
    set "JAVAC=%JAVA_HOME%\bin\javac.exe"
    set "JAR=%JAVA_HOME%\bin\jar.exe"
) else (
    set "JAVAC=javac"
    set "JAR=jar"
)
"%JAVAC%" -version 2>&1 | findstr /C:"25." >nul
if errorlevel 1 (echo GuessMarket Exercise 03 must be built with Java 25.& exit /b 1)
if not exist "%JAVAFX_SDK%\lib\javafx.controls.jar" (echo JAVAFX_SDK is invalid.& exit /b 1)
call build-server.bat
if errorlevel 1 exit /b 1
if exist "submission-build" rmdir /s /q "submission-build"
if not exist "submission" mkdir "submission"
if exist "submission\client" rmdir /s /q "submission\client"
if exist "submission\GuessMarket.war" del /q "submission\GuessMarket.war"
if exist "submission\GuessMarket-Core.jar" del /q "submission\GuessMarket-Core.jar"
if exist "submission\GuessMarket-JavaFX.jar" del /q "submission\GuessMarket-JavaFX.jar"
if exist "submission\lib" rmdir /s /q "submission\lib"
if exist "submission\run.bat" del /q "submission\run.bat"
mkdir "submission-build\core" "submission-build\javafx" "submission\client\lib\runtime" "submission\client\lib\javafx" "submission\client\lib\javafx-bin"
dir /s /b "guessmarket-core\src\*.java" "guessmarket-core\generated\*.java" > "submission-build\core-sources.txt"
"%JAVAC%" -encoding UTF-8 -cp ".deps\*" -d "submission-build\core" @"submission-build\core-sources.txt"
if errorlevel 1 exit /b 1
dir /s /b "guessmarket-javafx\src\*.java" > "submission-build\javafx-sources.txt"
"%JAVAC%" -encoding UTF-8 -cp "submission-build\core;.deps\*;%JAVAFX_SDK%\lib\*" -d "submission-build\javafx" @"submission-build\javafx-sources.txt"
if errorlevel 1 exit /b 1
copy /y "guessmarket-javafx\src\guessmarket\javafx\view\*.css" "submission-build\javafx\guessmarket\javafx\view\" >nul
"%JAR%" --create --file "submission\client\GuessMarket-Core.jar" -C "submission-build\core" .
if errorlevel 1 exit /b 1
"%JAR%" --create --file "submission\client\GuessMarket-JavaFX.jar" --main-class guessmarket.javafx.GuessMarketApplication -C "submission-build\javafx" .
if errorlevel 1 exit /b 1
copy /y "server-dist\GuessMarket.war" "submission\GuessMarket.war" >nul
copy /y "run.bat" "submission\client\run.bat" >nul
for %%F in (gson-2.14.0.jar jakarta.xml.bind-api-4.0.2.jar jaxb-core-4.0.5.jar jaxb-runtime-4.0.5.jar jakarta.activation-api-2.1.3.jar angus-activation-2.0.2.jar istack-commons-runtime-4.1.2.jar) do copy /y ".deps\%%F" "submission\client\lib\runtime\" >nul
for %%F in (javafx.base.jar javafx.graphics.jar javafx.controls.jar) do copy /y "%JAVAFX_SDK%\lib\%%F" "submission\client\lib\javafx\" >nul
xcopy /e /i /y "%JAVAFX_SDK%\bin" "submission\client\lib\javafx-bin" >nul
echo Submission assembled in %CD%\submission
endlocal
