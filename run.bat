@echo off
setlocal
set "APP_DIR=%~dp0"
if defined JAVA_HOME (set "JAVA_CMD=%JAVA_HOME%\bin\java.exe") else (set "JAVA_CMD=java")
"%JAVA_CMD%" -version 2>&1 | findstr /C:"25." >nul
if errorlevel 1 (
    echo GuessMarket Exercise 02 requires Java 25.
    echo Set JAVA_HOME to a Java 25 JDK or put Java 25 on PATH.
    exit /b 1
)
"%JAVA_CMD%" --module-path "%APP_DIR%lib\javafx" --add-modules javafx.controls --enable-native-access=javafx.graphics -Djava.library.path="%APP_DIR%lib\javafx-bin" -cp "%APP_DIR%GuessMarket-JavaFX.jar;%APP_DIR%GuessMarket-Core.jar;%APP_DIR%lib\jaxb\*" guessmarket.javafx.GuessMarketApplication
exit /b %errorlevel%
