@echo off
setlocal EnableExtensions
cd /d "%~dp0"
if not defined JAVAFX_SDK (echo Set JAVAFX_SDK to the JavaFX 25 SDK directory.& exit /b 1)
if not defined JAXB_HOME (echo Set JAXB_HOME to the JAXB RI directory containing mod.& exit /b 1)
if defined JAVA_HOME (
    set "JAVAC=%JAVA_HOME%\bin\javac.exe"
    set "JAR=%JAVA_HOME%\bin\jar.exe"
) else (
    set "JAVAC=javac"
    set "JAR=jar"
)
"%JAVAC%" -version 2>&1 | findstr /C:"25." >nul
if errorlevel 1 (echo GuessMarket Exercise 02 must be built with Java 25.& exit /b 1)
if not exist "%JAVAFX_SDK%\lib\javafx.controls.jar" (echo JAVAFX_SDK is invalid.& exit /b 1)
if not exist "%JAXB_HOME%\mod\jakarta.xml.bind-api.jar" (echo JAXB_HOME is invalid.& exit /b 1)
if exist "submission-build" rmdir /s /q "submission-build"
if exist "submission" rmdir /s /q "submission"
mkdir "submission-build\core" "submission-build\javafx" "submission\lib\jaxb" "submission\lib\javafx" "submission\lib\javafx-bin"
dir /s /b "guessmarket-core\src\*.java" "guessmarket-core\generated\*.java" > "submission-build\core-sources.txt"
"%JAVAC%" -cp "%JAXB_HOME%\mod\*" -d "submission-build\core" @"submission-build\core-sources.txt"
if errorlevel 1 exit /b 1
dir /s /b "guessmarket-javafx\src\*.java" > "submission-build\javafx-sources.txt"
"%JAVAC%" -cp "submission-build\core;%JAXB_HOME%\mod\*;%JAVAFX_SDK%\lib\*" -d "submission-build\javafx" @"submission-build\javafx-sources.txt"
if errorlevel 1 exit /b 1
xcopy /e /i /y "guessmarket-javafx\src\guessmarket\javafx\view" "submission-build\javafx\guessmarket\javafx\view" >nul
"%JAR%" --create --file "submission\GuessMarket-Core.jar" -C "submission-build\core" .
if errorlevel 1 exit /b 1
"%JAR%" --create --file "submission\GuessMarket-JavaFX.jar" --main-class guessmarket.javafx.GuessMarketApplication -C "submission-build\javafx" .
if errorlevel 1 exit /b 1
copy /y "run.bat" "submission\run.bat" >nul
copy /y "README.md" "submission\README.md" >nul
copy /y "README.rtf" "submission\README.rtf" >nul
for %%F in (angus-activation.jar jakarta.activation-api.jar jakarta.xml.bind-api.jar jaxb-core.jar jaxb-impl.jar) do copy /y "%JAXB_HOME%\mod\%%F" "submission\lib\jaxb\" >nul
for %%F in (javafx.base.jar javafx.graphics.jar javafx.controls.jar) do copy /y "%JAVAFX_SDK%\lib\%%F" "submission\lib\javafx\" >nul
xcopy /e /i /y "%JAVAFX_SDK%\bin" "submission\lib\javafx-bin" >nul
echo Submission assembled in %CD%\submission
endlocal
