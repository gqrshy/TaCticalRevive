# TacticalRevive Deploy Script
# Deploys to battle, main1, main2 servers
#
# Usage:
#   ./deploy.ps1           - Build and deploy
#   ./deploy.ps1 -NoBuild  - Deploy only (skip build)

param(
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"

# Configuration
$ProjectDir = $PSScriptRoot
$BuildDir = "$ProjectDir\build\libs"
$JarPattern = "tacticalrevive-*.jar"
$SourcesPattern = "*-sources.jar"

# Target servers
$Servers = @(
    "C:\mcss_win-x86-64_v13.9.2\servers\battle",
    "C:\mcss_win-x86-64_v13.9.2\servers\main1",
    "C:\mcss_win-x86-64_v13.9.2\servers\main2"
)

# Local client profiles
$ClientProfiles = @(
    "C:\Users\gqrsh\AppData\Roaming\ModrinthApp\profiles\ultimate cobblemon pack (1)",
    "C:\Users\gqrsh\AppData\Roaming\ModrinthApp\profiles\ultimate cobblemon pack"
)

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  TacticalRevive Deploy Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Build (optional)
if (-not $NoBuild) {
    Write-Host "[1/3] Building project..." -ForegroundColor Yellow
    Set-Location -LiteralPath $ProjectDir

    # Use gradlew.bat on Windows
    $GradleCmd = if (Test-Path -LiteralPath "$ProjectDir\gradlew.bat") { "$ProjectDir\gradlew.bat" } else { "gradle" }
    & $GradleCmd build

    if ($LASTEXITCODE -ne 0) {
        Write-Host "Build failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host "Build successful" -ForegroundColor Green
} else {
    Write-Host "[1/3] Skipping build (-NoBuild)" -ForegroundColor DarkGray
}

# Step 2: Find JAR (get latest one)
Write-Host "[2/3] Finding JAR file..." -ForegroundColor Yellow
$JarFile = Get-ChildItem -LiteralPath $BuildDir -Filter $JarPattern |
    Where-Object { $_.Name -notlike $SourcesPattern } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $JarFile) {
    Write-Host "JAR file not found in $BuildDir" -ForegroundColor Red
    Write-Host "Run without -NoBuild to build first" -ForegroundColor Yellow
    exit 1
}

Write-Host "Found: $($JarFile.Name) ($([math]::Round($JarFile.Length / 1MB, 2)) MB)" -ForegroundColor Green

# Step 3: Deploy to servers and clients
Write-Host "[3/3] Deploying..." -ForegroundColor Yellow
Write-Host ""

$serverCount = 0
$clientCount = 0

# Deploy to servers
Write-Host "--- Servers ---" -ForegroundColor Magenta
foreach ($serverPath in $Servers) {
    $serverName = Split-Path $serverPath -Leaf
    $targetDir = "$serverPath\mods"

    if (Test-Path $targetDir) {
        Write-Host "[$serverName]" -ForegroundColor Yellow

        # Remove old versions
        $oldJars = Get-ChildItem -Path $targetDir -Filter "tacticalrevive-*.jar" -ErrorAction SilentlyContinue
        if ($oldJars) {
            foreach ($old in $oldJars) {
                Remove-Item $old.FullName -Force -ErrorAction SilentlyContinue
                Write-Host "  Removed: $($old.Name)" -ForegroundColor DarkGray
            }
        }

        # Copy new JAR
        Copy-Item $JarFile.FullName -Destination $targetDir -Force
        Write-Host "  Deployed: $($JarFile.Name)" -ForegroundColor Green
        $serverCount++
    } else {
        Write-Host "[$serverName] Target not found: $targetDir" -ForegroundColor Red
    }
}

# Deploy to client profiles
Write-Host ""
Write-Host "--- Clients ---" -ForegroundColor Magenta
foreach ($profilePath in $ClientProfiles) {
    $profileName = Split-Path $profilePath -Leaf
    $targetDir = "$profilePath\mods"

    if (Test-Path -LiteralPath $targetDir) {
        Write-Host "[$profileName]" -ForegroundColor Yellow

        # Remove old versions
        $oldJars = Get-ChildItem -LiteralPath $targetDir -Filter "tacticalrevive-*.jar" -ErrorAction SilentlyContinue
        if ($oldJars) {
            foreach ($old in $oldJars) {
                Remove-Item -LiteralPath $old.FullName -Force -ErrorAction SilentlyContinue
                Write-Host "  Removed: $($old.Name)" -ForegroundColor DarkGray
            }
        }

        # Copy new JAR
        Copy-Item -LiteralPath $JarFile.FullName -Destination $targetDir -Force
        Write-Host "  Deployed: $($JarFile.Name)" -ForegroundColor Green
        $clientCount++
    } else {
        Write-Host "[$profileName] Target not found: $targetDir" -ForegroundColor Red
    }
}

# Summary
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Deployment Complete" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Servers: $serverCount / $($Servers.Count)" -ForegroundColor White
Write-Host "Clients: $clientCount / $($ClientProfiles.Count)" -ForegroundColor White
Write-Host "JAR: $($JarFile.Name)" -ForegroundColor White
Write-Host ""
Write-Host "Restart servers/clients to apply changes." -ForegroundColor Yellow
Write-Host ""
