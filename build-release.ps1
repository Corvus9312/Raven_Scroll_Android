$propsFile = Join-Path $PSScriptRoot "keystore.properties"
if (-not (Test-Path $propsFile)) {
    Write-Error "keystore.properties not found"
    exit 1
}

foreach ($line in Get-Content $propsFile) {
    if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
    $key, $val = $line -split '=', 2
    [System.Environment]::SetEnvironmentVariable($key.Trim(), $val.Trim(), 'Process')
}

$gradlew = Join-Path $PSScriptRoot "gradlew.bat"
$gradle  = "C:\Users\k1223\.gradle\wrapper\dists\gradle-8.7-bin\bhs2wmbdwecv87pi65oeuq5iu\gradle-8.7\bin\gradle.bat"

$runner = if (Test-Path $gradlew) { $gradlew } elseif (Test-Path $gradle) { $gradle } else {
    Write-Error "Cannot find gradlew or gradle"
    exit 1
}

if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
}

Write-Host "Building release APK with: $runner"
& $runner assembleRelease -p $PSScriptRoot
if ($LASTEXITCODE -ne 0) { Write-Error "Build failed"; exit 1 }

$apk = "$PSScriptRoot\app\build\outputs\apk\release\app-release.apk"

$gradleFile = "$PSScriptRoot\app\build.gradle.kts"
$version = (Select-String 'versionName\s*=\s*"([^"]+)"' $gradleFile).Matches[0].Groups[1].Value
$outApk = "$PSScriptRoot\app\build\outputs\apk\release\raven-scroll-$version.apk"
Rename-Item -Path $apk -NewName (Split-Path $outApk -Leaf) -Force

Write-Host "Done! APK: $outApk"
