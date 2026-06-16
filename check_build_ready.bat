@echo off
echo ===================================
echo ChessOmania Build Readiness Check
echo ===================================
echo.

REM Check 1: Java
echo [1/5] Checking Java...
java -version >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [OK] Java is installed
    java -version 2>&1 | findstr /C:"version"
) else (
    echo [ERROR] Java not found! Install Java 17
    goto :end
)
echo.

REM Check 2: Android SDK
echo [2/5] Checking Android SDK...
if exist "ChessomaniaApp\local.properties" (
    echo [OK] local.properties exists
    findstr "sdk.dir" ChessomaniaApp\local.properties
) else (
    echo [ERROR] local.properties missing!
    echo Create ChessomaniaApp\local.properties with:
    echo sdk.dir=C:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
    goto :end
)
echo.

REM Check 3: Gradle wrapper
echo [3/5] Checking Gradle wrapper...
if exist "ChessomaniaApp\gradlew.bat" (
    echo [OK] gradlew.bat exists
) else (
    echo [ERROR] gradlew.bat missing!
    goto :end
)
echo.

REM Check 4: Critical source files
echo [4/5] Checking critical source files...
set ERROR_COUNT=0

if not exist "ChessomaniaApp\app\src\main\java\com\chessomania\app\ui\PlayFragment.kt" (
    echo [ERROR] PlayFragment.kt missing
    set /a ERROR_COUNT+=1
)

if not exist "ChessomaniaApp\app\src\main\java\com\chessomania\app\SettingsManager.kt" (
    echo [ERROR] SettingsManager.kt missing
    set /a ERROR_COUNT+=1
)

if not exist "ChessomaniaApp\app\src\main\java\com\chessomania\app\net\NetworkClient.kt" (
    echo [ERROR] NetworkClient.kt missing
    set /a ERROR_COUNT+=1
)

if not exist "ChessomaniaApp\app\src\main\java\com\chessomania\app\net\SecurePrefs.kt" (
    echo [ERROR] SecurePrefs.kt missing
    set /a ERROR_COUNT+=1
)

if not exist "ChessomaniaApp\app\src\main\java\com\chessomania\app\net\SSEService.kt" (
    echo [ERROR] SSEService.kt missing
    set /a ERROR_COUNT+=1
)

if not exist "ChessomaniaApp\app\src\main\java\com\chessomania\app\net\SseEvent.kt" (
    echo [ERROR] SseEvent.kt missing
    set /a ERROR_COUNT+=1
)

if %ERROR_COUNT% EQU 0 (
    echo [OK] All critical source files present
) else (
    echo [ERROR] %ERROR_COUNT% source files missing!
    goto :end
)
echo.

REM Check 5: AndroidManifest
echo [5/5] Checking AndroidManifest...
if exist "ChessomaniaApp\app\src\main\AndroidManifest.xml" (
    echo [OK] AndroidManifest.xml exists
    findstr /C:"SSEService" ChessomaniaApp\app\src\main\AndroidManifest.xml >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo [OK] SSEService declared in manifest
    ) else (
        echo [WARNING] SSEService might not be declared in manifest
    )
) else (
    echo [ERROR] AndroidManifest.xml missing!
    goto :end
)
echo.

echo ===================================
echo All checks passed! Ready to build!
echo ===================================
echo.
echo Next step: Run build_app.bat
echo.
goto :end

:end
pause
