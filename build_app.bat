@echo off
echo Building ChessOmania Android App...
cd ChessomaniaApp
call gradlew.bat clean assembleDebug --stacktrace
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo BUILD SUCCESS!
    echo ========================================
    echo APK Location: app\build\outputs\apk\debug\app-debug.apk
    echo.
) else (
    echo.
    echo ========================================
    echo BUILD FAILED! Error code: %ERRORLEVEL%
    echo ========================================
    echo Check the error messages above
    echo.
)
pause
