@echo off
setlocal

set KEYSTORE_NAME=qmetronome-upload-key.jks
set ALIAS=qmetronome-upload
set VALIDITY=10000

echo --- qMetronome Release Key Generator ---
echo.
echo This script will generate an upload key for Google Play.
echo You will be prompted for a keystore password and your organization details.
echo.
echo IMPORTANT: Store the generated %KEYSTORE_NAME% and its password securely!
echo If you lose them, you will lose the ability to update the app on Google Play.
echo.

keytool -genkeypair -v -keystore %KEYSTORE_NAME% -alias %ALIAS% -keyalg RSA -keysize 2048 -validity %VALIDITY%

if %ERRORLEVEL% EQU 0 (
    echo.
    echo Success! Generated %KEYSTORE_NAME% in the current directory.
    echo Now create a 'keystore.properties' file in the project root with these values:
    echo.
    echo storeFile=../%KEYSTORE_NAME%
    echo storePassword=YOUR_PASSWORD
    echo keyAlias=%ALIAS%
    echo keyPassword=YOUR_PASSWORD
) else (
    echo.
    echo Error: keytool failed. Make sure JDK is installed and in your PATH.
)

pause
