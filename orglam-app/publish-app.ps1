# Builds the Orglam app and publishes it to every phone.
# Run it by double-clicking publish-app.bat (which just launches this).

$ErrorActionPreference = 'Stop'

$Worker = 'https://dawn-king-6cc3.orglam-service.workers.dev'
$Root   = $PSScriptRoot
$Gradle = Join-Path $Root 'android\app\build.gradle'
$Apk    = Join-Path $Root 'android\app\build\outputs\apk\debug\app-debug.apk'

function Fail($msg) {
  # Puts the version number back, so a failed attempt doesn't burn a version.
  if ($script:gradleBackup) {
    try { Set-Content -NoNewline -Path $script:Gradle -Value $script:gradleBackup } catch { }
  }
  Write-Host ''
  Write-Host $msg -ForegroundColor Red
  Write-Host ''
  Read-Host 'Press Enter to close'
  exit 1
}

if (-not (Test-Path $Gradle)) {
  Fail "Could not find android\app\build.gradle`nPut publish-app.bat and publish-app.ps1 in the orglam-app folder, next to the 'android' folder."
}

# Gradle 8.x runs on Java 17-24 ONLY: a 32-bit Java 8 gives "could not reserve enough
# space for object heap", and Java 25 gives "Unsupported class file major version 69".
# So candidates are probed and the first usable one wins, rather than forcing a path.
function Get-JavaMajor($javaHome) {
  if (-not $javaHome) { return 0 }
  $exe = Join-Path $javaHome 'bin\java.exe'
  if (-not (Test-Path $exe)) { return 0 }

  # Read the JDK's own "release" file first. Running java -version is the obvious way and
  # the wrong one here: java writes its version to STDERR, and with ErrorActionPreference
  # 'Stop' PowerShell turns any native stderr output into a thrown error - so every probe
  # failed and the script reported "none found" on a PC that had several JDKs.
  $releaseFile = Join-Path $javaHome 'release'
  if (Test-Path $releaseFile) {
    $raw = Get-Content -Raw $releaseFile
    # Legacy first: "1.8.0_461" would otherwise match the plain-number branch and read as "Java 1".
    $m = [regex]::Match($raw, 'JAVA_VERSION="?1\.(\d+)')
    if ($m.Success) { return [int]$m.Groups[1].Value }
    $m = [regex]::Match($raw, 'JAVA_VERSION="?(\d+)')
    if ($m.Success) { return [int]$m.Groups[1].Value }
  }

  # No release file (older JREs): ask java, with stderr allowed through.
  $prev = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  try {
    $out = (& $exe -version 2>&1 | Out-String)
    if ($out -match 'version "1\.(\d+)') { return [int]$Matches[1] }
    $m = [regex]::Match($out, 'version "(\d+)')
    if ($m.Success) { return [int]$m.Groups[1].Value }
  } catch {
  } finally {
    $ErrorActionPreference = $prev
  }
  return 0
}

# Candidates: what the shell already uses, then a bounded search of the places JDKs
# actually land. The previous fixed-path list found nothing on this machine, which is
# why this looks rather than assumes.
$candidates = @()
if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }

# Whatever "java" resolves to on PATH - its home is two levels up from the exe.
try {
  $onPath = (Get-Command java -ErrorAction SilentlyContinue).Source
  if ($onPath) { $candidates += (Split-Path (Split-Path $onPath -Parent) -Parent) }
} catch { }

# Gradle may already be told which JDK to use, in which case that is the truth.
$gradleProps = Join-Path $Root 'android\gradle.properties'
if (Test-Path $gradleProps) {
  $m = [regex]::Match((Get-Content -Raw $gradleProps), 'org\.gradle\.java\.home\s*=\s*(.+)')
  if ($m.Success) { $candidates += $m.Groups[1].Value.Trim().Replace('\\', '\') }
}

$roots = @(
  $env:ProgramFiles,
  [Environment]::GetEnvironmentVariable('ProgramFiles(x86)'),
  "$env:LOCALAPPDATA\Programs",
  $env:ProgramData,
  "$env:USERPROFILE\.gradle\jdks",
  'C:\Java', 'D:\Java'
) | Where-Object { $_ -and (Test-Path $_) }

Write-Host 'Looking for a usable Java...' -ForegroundColor DarkGray
foreach ($searchRoot in $roots) {
  # Only folders that plausibly hold a JDK, and only a few levels down, so this stays quick.
  $isJdkStore = $searchRoot -like '*\.gradle\jdks'
  $dirs = Get-ChildItem $searchRoot -Directory -ErrorAction SilentlyContinue |
    Where-Object { $isJdkStore -or $_.Name -match 'java|jdk|jre|android studio|adoptium|temurin|zulu|corretto|microsoft|liberica|graalvm' }
  foreach ($d in $dirs) {
    $candidates += $d.FullName
    $candidates += (Get-ChildItem $d.FullName -Directory -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName })
  }
}

$seen = @{}
$found = @()
$picked = $null
foreach ($c in $candidates) {
  if (-not $c) { continue }
  $key = $c.TrimEnd('\').ToLower()
  if ($seen.ContainsKey($key)) { continue }
  $seen[$key] = $true
  $major = Get-JavaMajor $c
  if (-not $major) { continue }
  $found += "  Java $major - $c"
  if ($major -ge 17 -and $major -le 24 -and (-not $picked -or $major -gt $pickedMajor)) { $picked = $c; $pickedMajor = $major }
}

if ($picked) {
  $env:JAVA_HOME = $picked
  Write-Host "Using Java $pickedMajor at $picked" -ForegroundColor DarkGray
} else {
  $list = if ($found.Count) { ($found | Select-Object -Unique) -join "`n" } else { '  (none found)' }
  $pathJava = try { (Get-Command java -ErrorAction SilentlyContinue).Source } catch { $null }
  if ($pathJava) { $list = "$list`n`nGradle currently falls back to: $pathJava" }
  Fail "Gradle needs Java 17-24. Java 25 is too new (that is the 'class file major version 69' error) and Java 8 is too old.`n`nWhat this PC has:`n$list`n`nInstall JDK 21 from https://adoptium.net (take the .msi, default options) and run this again - it will be found automatically."
}

Write-Host ''
Write-Host '=== 1/4  Raising the version number ===' -ForegroundColor Cyan

# The number published MUST be the one baked into the APK: the phones compare it
# against their own installed versionCode, so a made-up number would either
# prompt forever or never prompt at all.
$content = Get-Content -Raw $Gradle
$match = [regex]::Match($content, 'versionCode\s+(\d+)')
if (-not $match.Success) { Fail "No versionCode line found in $Gradle" }

$newVersion = [int]$match.Groups[1].Value + 1
$content = [regex]::Replace($content, 'versionCode\s+\d+', "versionCode $newVersion", 'None', 1)
$content = [regex]::Replace($content, 'versionName\s+"[^"]*"', "versionName `"$newVersion.0`"", 'None', 1)
$gradleBackup = Get-Content -Raw $Gradle   # restored if anything below fails
Set-Content -NoNewline -Path $Gradle -Value $content
Write-Host "Building version $newVersion"

Write-Host ''
Write-Host '=== 2/4  Copying the dashboard into the app ===' -ForegroundColor Cyan
$capLocal = Join-Path $Root 'node_modules\.bin\cap.cmd'
if (Test-Path $capLocal) {
  cmd /c "cd /d `"$Root`" && `"$capLocal`" copy android"
} else {
  cmd /c "cd /d `"$Root`" && npx cap copy android"
}
if ($LASTEXITCODE -ne 0) { Fail "Copying the web files failed - nothing was published.`n`nIf npx says it cannot find 'cap', run  npm install  in $Root once." }

Write-Host ''
Write-Host '=== 3/4  Building the app (takes a couple of minutes) ===' -ForegroundColor Cyan
$androidDir = Join-Path $Root 'android'
cmd /c "cd /d `"$androidDir`" && gradlew assembleDebug"
if ($LASTEXITCODE -ne 0) { Fail 'The build failed - nothing was published.' }

# Gradle's output name depends on the signing setup (app-release.apk when signed,
# app-release-unsigned.apk when not), so whatever landed in the folder is used.
if (-not (Test-Path $Apk)) {
  $outDir = Join-Path $Root 'android\app\build\outputs\apk\debug'
  $candidate = if (Test-Path $outDir) {
    Get-ChildItem $outDir -Filter '*.apk' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
  } else { $null }
  if (-not $candidate) {
    $listing = if (Test-Path $outDir) { (Get-ChildItem $outDir | ForEach-Object { '  ' + $_.Name }) -join "`n" } else { '  (the folder does not exist)' }
    Fail "Build finished but no .apk was found in:`n  $outDir`n`nWhat is there:`n$listing"
  }
  $Apk = $candidate.FullName
  Write-Host "Using $($candidate.Name)" -ForegroundColor DarkGray
}

$sizeMb = [math]::Round((Get-Item $Apk).Length / 1MB, 1)
if ($sizeMb -gt 45) {
  Fail "That build is $sizeMb MB - the publish limit is 45 MB."
}

Write-Host ''
Write-Host "=== 4/4  Publishing version $newVersion to every phone ($sizeMb MB) ===" -ForegroundColor Cyan

try {
  $url = "$Worker/app-upload?versionCode=$newVersion&by=$env:USERNAME"
  $res = Invoke-RestMethod -Uri $url -Method Put -InFile $Apk -ContentType 'application/vnd.android.package-archive'
} catch {
  Fail "Upload failed: $($_.Exception.Message)`nIs the worker deployed with the new /app-upload route?"
}

if (-not $res.ok) { Fail "The server refused it: $($res.error)" }

Write-Host ''
Write-Host "Done - version $newVersion is live." -ForegroundColor Green
Write-Host 'Staff are prompted to update next time they open the app.'
Write-Host ''
Read-Host 'Press Enter to close'
