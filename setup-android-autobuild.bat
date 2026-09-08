@echo off
REM ===================================================================
REM  Lets GitHub build and publish the Android app on every push.
REM  Run ONCE, from the folder that contains "orglam-app".
REM
REM  It does two things:
REM    1. Teaches the app how to sign itself from GitHub.
REM    2. Exports the key your installed app was signed with, so an
REM       update installs straight over what people already have.
REM ===================================================================

cd /d "%~dp0"

set GRADLE=orglam-app\android\app\build.gradle
set KS=%USERPROFILE%\.android\debug.keystore

if not exist "%GRADLE%" (
  echo Could not find %GRADLE% - run this from the orglam dashboard folder.
  goto end
)
if not exist "%KS%" (
  echo.
  echo  Could not find the key your app was signed with:
  echo    %KS%
  echo.
  echo  That file is what lets an update install over the existing app.
  echo  Tell me and I will set up a brand-new key instead - but then
  echo  everyone has to uninstall and reinstall the app once.
  goto end
)

echo === 1/3  Teaching the app to sign itself ===
REM The signing block is added only if it isn't there yet, and it falls back to nothing when the
REM key file is absent - so a local build on this PC keeps working exactly as before.
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$f='%GRADLE%'; $c=Get-Content -Raw $f; if ($c -match 'signingConfigs') { Write-Host '    already set up'; exit 0 }; $block=@'
    // Signing: GitHub restores the keystore next to this file and passes the passwords in as
    // environment variables. When the file is absent (a plain local build) the release type is
    // left unsigned exactly as before, so nothing on this PC changes.
    def orglamKeystore = file(System.getenv(''ORGLAM_KEYSTORE_PATH'') ?: ''release.jks'')
    signingConfigs {
        release {
            if (orglamKeystore.exists()) {
                storeFile orglamKeystore
                storePassword System.getenv(''ORGLAM_STORE_PASSWORD'') ?: ''android''
                keyAlias System.getenv(''ORGLAM_KEY_ALIAS'') ?: ''androiddebugkey''
                keyPassword System.getenv(''ORGLAM_KEY_PASSWORD'') ?: ''android''
            }
        }
    }
    buildTypes {
        release {
            if (orglamKeystore.exists()) signingConfig signingConfigs.release
'@; $c = $c -replace '(?m)^    buildTypes \{\r?\n        release \{', $block; Set-Content -NoNewline -Path $f -Value $c; Write-Host '    done'"

findstr /c:"signingConfigs" "%GRADLE%" >nul 2>&1
if errorlevel 1 (
  echo.
  echo  Could not patch build.gradle automatically. Send me its contents.
  goto end
)

echo.
echo === 2/3  Exporting your signing key ===
powershell -NoProfile -Command "[Convert]::ToBase64String([IO.File]::ReadAllBytes('%KS%')) | Set-Content -NoNewline 'android-keystore-base64.txt'"
findstr /x /c:"android-keystore-base64.txt" .gitignore >nul 2>&1
if errorlevel 1 >> .gitignore echo android-keystore-base64.txt
echo     written to android-keystore-base64.txt

echo.
echo === 3/3  What to add on GitHub ===
echo.
echo  Open:
echo    https://github.com/Hendyoussef141/orglam-dashboard/settings/secrets/actions
echo.
echo  Add these four secrets:
echo.
echo    ANDROID_KEYSTORE_BASE64   = the whole contents of android-keystore-base64.txt
echo                                (open it in Notepad, Ctrl+A, Ctrl+C)
echo    ANDROID_STORE_PASSWORD    = android
echo    ANDROID_KEY_ALIAS         = androiddebugkey
echo    ANDROID_KEY_PASSWORD      = android
echo.
echo  Then open the "Variables" tab on that same page and add:
echo    BUILD_ANDROID = true
echo.
echo  Finally run deploy.bat. Every push then builds the app and
echo  publishes it to every phone by itself.
echo.
echo  Delete android-keystore-base64.txt once the secret is saved.

:end
echo.
pause
