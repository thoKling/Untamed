<#
.SYNOPSIS
    Builds the mod and installs the jar into the Minecraft mods folder.

.EXAMPLE
    .\deploy.ps1
    .\deploy.ps1 -MinecraftDir 'D:\games\.minecraft'
#>
[CmdletBinding()]
param(
    [string] $MinecraftDir = (Join-Path $env:APPDATA '.minecraft'),
    [switch] $SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

# The artifact name is derived from gradle.properties rather than hard-coded, so
# a version bump does not silently leave an old jar in the mods folder.
$props = @{}
Get-Content (Join-Path $root 'gradle.properties') | ForEach-Object {
    if ($_ -match '^\s*([^#=\s]+)\s*=\s*(.+?)\s*$') { $props[$Matches[1]] = $Matches[2] }
}
$baseName = $props['archives_base_name']
$jarName  = "$baseName-$($props['mod_version']).jar"
$jarPath  = Join-Path $root "build\libs\$jarName"

if (-not $SkipBuild) {
    Write-Host "Building $jarName ..." -ForegroundColor Cyan
    & (Join-Path $root 'gradlew.bat') build
    if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE." }
}

if (-not (Test-Path $jarPath)) { throw "Expected jar not found: $jarPath" }

$modsDir = Join-Path $MinecraftDir 'mods'
if (-not (Test-Path $modsDir)) { New-Item -ItemType Directory -Path $modsDir | Out-Null }

# Minecraft keeps an open handle on every mod jar while it runs, so the copy
# fails rather than taking effect. Say so plainly instead of leaking the IO error.
try {
    Copy-Item $jarPath $modsDir -Force
} catch [System.IO.IOException] {
    throw "Could not write to $modsDir. Close Minecraft and run this again."
}

# A jar left over from an earlier version would load alongside the new one and
# lose the mod-id conflict, so clear out any sibling build of this mod.
Get-ChildItem $modsDir -Filter "$baseName-*.jar" |
    Where-Object { $_.Name -ne $jarName } |
    ForEach-Object {
        Write-Host "Removing stale $($_.Name)" -ForegroundColor Yellow
        Remove-Item $_.FullName -Force
    }

Write-Host "Installed $jarName to $modsDir" -ForegroundColor Green
