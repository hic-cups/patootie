# Windows version of setup-and-build.sh. Run it from Command Prompt with:
#
#   scripts\setup-and-build.cmd
#
# Downloads everything Patootie needs and builds it: a JDK 17 (only if you don't
# have Java 17+), the Android SDK pieces, and all Gradle dependencies.
# Nothing is installed system-wide; it all goes into .local\ next to the project.
# Uses ANDROID_HOME instead of downloading an SDK if you already have one.
# Works with Windows PowerShell 5.1 (built into Windows 10/11) and PowerShell 7.

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # Invoke-WebRequest is painfully slow with the progress bar
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Root = Split-Path -Parent $PSScriptRoot
$Local = Join-Path $Root '.local'
$CmdlineToolsBuild = '11076708'

function Say($text) { Write-Host ''; Write-Host "* $text" -ForegroundColor Magenta }
function Die($text) { Write-Host ''; Write-Host "X $text" -ForegroundColor Red; exit 1 }

function Download($url, $out) {
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($curl) {
        & $curl.Source -fL --retry 3 -o $out $url
        if ($LASTEXITCODE -ne 0) { Die "Download failed: $url" }
    } else {
        Invoke-WebRequest -Uri $url -OutFile $out -UseBasicParsing
    }
}

function Unzip($zip, $dest) {
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    $tar = Get-Command tar.exe -ErrorAction SilentlyContinue
    if ($tar) {
        & $tar.Source -xf $zip -C $dest
        if ($LASTEXITCODE -ne 0) { Die "Couldn't unpack $zip" }
    } else {
        Expand-Archive -Path $zip -DestinationPath $dest -Force
    }
}

# Asks a java executable for its major version and real home directory (the
# java.exe on PATH is often Oracle's "javapath" shim, not inside the JDK).
function JavaInfo($java) {
    $ErrorActionPreference = 'Continue'   # java prints all of this to stderr
    $out = & $java -XshowSettings:properties -version 2>&1 | Out-String
    $major = 0
    $javaHome = $null
    if ($out -match 'version "(\d+)') { $major = [int]$Matches[1] }
    if ($out -match 'java\.home = (.+)') { $javaHome = $Matches[1].Trim() }
    return @{ Major = $major; Home = $javaHome }
}

$arch = if ($env:PROCESSOR_ARCHITECTURE -eq 'ARM64') { 'aarch64' } else { 'x64' }
New-Item -ItemType Directory -Force -Path $Local | Out-Null

# --- Java 17+ -------------------------------------------------------------
$javaExe = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
} else {
    $found = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($found) { $javaExe = $found.Source }
}

$info = if ($javaExe) { JavaInfo $javaExe } else { @{ Major = 0; Home = $null } }
if ($info.Major -ge 17 -and $info.Home) {
    Say "Using your Java $($info.Major)"
    $env:JAVA_HOME = $info.Home
} else {
    $jdkDir = Join-Path $Local 'jdk-17'
    if (-not (Test-Path (Join-Path $jdkDir 'bin\java.exe'))) {
        Say 'Downloading JDK 17 (Eclipse Temurin)...'
        $zip = Join-Path $Local 'jdk.zip'
        $tmp = Join-Path $Local 'jdk-download'
        if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp }
        Download "https://api.adoptium.net/v3/binary/latest/17/ga/windows/$arch/jdk/hotspot/normal/eclipse" $zip
        Unzip $zip $tmp
        $java = Get-ChildItem -Path $tmp -Recurse -Filter java.exe | Where-Object { $_.Directory.Name -eq 'bin' } | Select-Object -First 1
        if (-not $java) { Die "Couldn't find java.exe in the JDK download." }
        if (Test-Path $jdkDir) { Remove-Item -Recurse -Force $jdkDir }
        Move-Item -Path $java.Directory.Parent.FullName -Destination $jdkDir
        Remove-Item -Recurse -Force $tmp, $zip
    }
    $env:JAVA_HOME = $jdkDir
    Say 'Using bundled JDK 17'
}
$env:Path = "$(Join-Path $env:JAVA_HOME 'bin');$env:Path"

# --- Android SDK ------------------------------------------------------------
if ($env:ANDROID_HOME) { $sdk = $env:ANDROID_HOME }
elseif ($env:ANDROID_SDK_ROOT) { $sdk = $env:ANDROID_SDK_ROOT }
else { $sdk = Join-Path $Local 'android-sdk' }
$env:ANDROID_HOME = $sdk

$sdkmanager = Join-Path $sdk 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path $sdkmanager)) {
    Say 'Downloading Android command-line tools...'
    $zip = Join-Path $Local 'cmdline-tools.zip'
    $tools = Join-Path $sdk 'cmdline-tools'
    Download "https://dl.google.com/android/repository/commandlinetools-win-$($CmdlineToolsBuild)_latest.zip" $zip
    foreach ($d in 'latest', 'cmdline-tools') {
        $p = Join-Path $tools $d
        if (Test-Path $p) { Remove-Item -Recurse -Force $p }
    }
    Unzip $zip $tools
    Move-Item -Path (Join-Path $tools 'cmdline-tools') -Destination (Join-Path $tools 'latest')
    Remove-Item -Force $zip
}

Say 'Accepting Android SDK licenses...'
(@('y') * 50) | & $sdkmanager "--sdk_root=$sdk" --licenses | Out-Null

Say 'Installing Android platform 35 and build tools (skips what is already there)...'
& $sdkmanager "--sdk_root=$sdk" 'platforms;android-35' 'build-tools;35.0.0' 'platform-tools'
if ($LASTEXITCODE -ne 0) { Die 'sdkmanager failed (see the messages above).' }

# Forward slashes keep the .properties file free of backslash escaping.
Set-Content -Path (Join-Path $Root 'local.properties') -Value "sdk.dir=$($sdk -replace '\\', '/')" -Encoding ASCII

# --- Build ----------------------------------------------------------------
Say 'Building (first run downloads Gradle and all libraries; give it a few minutes)...'
Push-Location $Root
try {
    & (Join-Path $Root 'gradlew.bat') --no-daemon ':engine:test' ':app:assembleDebug' ':app:assembleRelease'
    if ($LASTEXITCODE -ne 0) { Die 'The build failed. Send Claude the last ~30 lines above.' }
} finally {
    Pop-Location
}

Say 'Done! Your APKs:'
Get-ChildItem -Path (Join-Path $Root 'app\build\outputs\apk') -Recurse -Filter *.apk | ForEach-Object { Write-Host "   $($_.FullName)" }
if (-not $env:PATOOTIE_KEYSTORE_FILE) {
    Write-Host '   (The release APK is signed with your local debug key. See README -> Release builds for a real key.)'
}
Write-Host ''
Write-Host '   Install on a connected phone with:'
Write-Host "   `"$(Join-Path $sdk 'platform-tools\adb.exe')`" install -r app\build\outputs\apk\release\app-release.apk"
