param(
    [switch]$Verbose,
    [switch]$Build,
    [switch]$NoGit
)

# === Launch Dev Client ===
$RuneLiteDir = "$env:LocalAppData\RuneLite"
$ProjectDir  = "C:\Users\Vince\source\repos\GrokSandbox\ChatBet\RuneLite"
$JarPath     = "C:\Users\Vince\source\repos\GrokSandbox\ChatBet\RuneLite\build\libs\chatbet-unspecified-all.jar"
$Failed      = $false

if ($Build) {
    Set-Location $ProjectDir
    if (!$NoGit) {
        Write-Host "Pulling latest files..."
        git pull origin main
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
        Write-Host "Build Failed! Exiting! Error Log copied to clipboard!"
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
    "com.vxv.chatbet.ChatBetPluginTest",
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
    elseif ($_ -match "chatbet|ChatBet") {
        Write-Host $_ -ForegroundColor Green
    }
    elseif ($Verbose) {
        Write-Host $_
    }
}

Set-Location $ProjectDir