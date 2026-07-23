param(
    [switch]$Verbose,
    [switch]$Build,
    [switch]$NoGit,
    [switch]$ExpectGit
)

# === Launch Dev Client ===
$RuneLiteDir = "$env:LocalAppData\RuneLite"
$ProjectDir  = "C:\Users\Vira\source\repos\GrokSandbox\RuneBridge\RuneLite"
# Must match settings.gradle rootProject.name + version + "-all.jar"
# (rootProject.name is "rune-bridge", version is "1.0.0" in build.gradle)
$JarPath     = "C:\Users\Vira\source\repos\GrokSandbox\RuneBridge\RuneLite\build\libs\rune-bridge-1.0.0-all.jar"
$Failed      = $false

if ($Build) {
    Set-Location $ProjectDir
    if (!$NoGit) {
        Write-Host "Pulling latest files..."
        if ($ExpectGit) {
            $output = ""
            & git pull origin main 2>&1 | ForEach-Object {
                if ($_ -match "Already up to date.") {
                    Write-Host "No Git update detected... Exiting." -ForegroundColor Red
                    Exit
                }
            }
        }
        else {
            git pull origin main
        }
    }
    Write-Host "Building ShadowJar..."
    $BuildOutput = ""
    & .\gradlew.bat clean shadowJar 2>&1 | ForEach-Object {
        if ($_ -match "BUILD FAILED") {
            $Failed = $true
        }
        $BuildOutput += $_
        $BuildOutput += "`r`n"
    }
    if ($Failed) {
        Write-Host "Build Failed! Exiting! Error Log copied to clipboard!" -ForegroundColor Red
        $BuildOutput | Set-Clipboard
        Exit
    }
    else {
        Write-Host "Build succeeded! Launching..."
    }
}

Set-Location $RuneLiteDir

$javaArgs = @(
    "-Dfile.encoding=UTF-8",
    "-Duser.country=US",
    "-Duser.language=en",
    "-Duser.variant",
    "-ea",
    "-cp", $JarPath,
    "com.vxv.runebridge.RuneBridgePluginTest",
    "--debug",
    "--developer-mode"
)

& java @javaArgs 2>&1 | ForEach-Object {
    if ($_ -match "ERROR|Exception|Failed") {
        Write-Host $_ -ForegroundColor Red
    }
    elseif ($_ -match "WARN") {
        Write-Host $_ -ForegroundColor Yellow
    }
    elseif ($_ -match "runebridge|RuneBridge") {
        Write-Host $_ -ForegroundColor Green
    }
    elseif ($Verbose) {
        Write-Host $_
    }
}

Set-Location $ProjectDir