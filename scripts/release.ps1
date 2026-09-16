<#
.SYNOPSIS
    Cuts a release: sets the version, builds and tests, signs, tags, pushes and
    publishes the APK as a GitHub release. Every installed copy then offers it.

.EXAMPLE
    .\scripts\release.ps1 -Version 0.2.0 -NotesFile notes.md
    .\scripts\release.ps1 -Version 0.2.0 -Notes "One-line summary" -DryRun

.NOTES
    Only when Edgar asks for a release. Requires git, gh (authenticated) and the
    release key described in weblauncher-release.properties under
    %USERPROFILE%\.android. The tag message IS the release notes, and the app
    shows them before installing. Pure ASCII, Windows PowerShell 5.1.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Version,
    [string]$Notes,
    [string]$NotesFile,
    [switch]$DryRun,
    # Releases come from main; a branch is only for trying the updater itself.
    [switch]$AllowBranch
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Die([string]$message) {
    Write-Host ""
    Write-Host "[release] $message" -ForegroundColor Red
    exit 1
}

function Say([string]$message) {
    Write-Host "[release] $message" -ForegroundColor Cyan
}

# Runs a native command and stops the release if it fails.
function Run([string]$exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { Die "'$exe $($arguments -join ' ')' failed (exit $LASTEXITCODE)" }
}

# Runs a native command and returns its trimmed output.
function Capture([string]$exe, [string[]]$arguments) {
    $output = & $exe @arguments
    if ($LASTEXITCODE -ne 0) { Die "'$exe $($arguments -join ' ')' failed (exit $LASTEXITCODE)" }
    return (($output | Out-String).Trim())
}

function Parse-Version([string]$text) {
    if ($text -match '^v?(\d+)\.(\d+)\.(\d+)$') {
        return [version]::new([int]$Matches[1], [int]$Matches[2], [int]$Matches[3])
    }
    return $null
}

function Write-Utf8([string]$path, [string]$text) {
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding $false))
}

# --- 1. Arguments ----------------------------------------------------------

$wanted = Parse-Version $Version
if ($null -eq $wanted -or $Version.StartsWith('v')) { Die "Invalid -Version '$Version' (expected X.Y.Z)" }
if ($wanted.Major -gt 99 -or $wanted.Minor -gt 99 -or $wanted.Build -gt 99) { Die "Each part of the version must be 0-99: the version code is built from them" }
$tag = "v$Version"

if ($NotesFile) {
    if (-not (Test-Path $NotesFile)) { Die "-NotesFile not found: $NotesFile" }
    $Notes = [System.IO.File]::ReadAllText((Resolve-Path $NotesFile))
}
if (-not $Notes -or -not $Notes.Trim()) { Die 'Missing release notes: pass -NotesFile <path> or -Notes "text"' }
$Notes = $Notes.Trim() + "`n"

Say "preparing $tag$(if ($DryRun) { ' (dry run)' })"

# --- 2. Repository state ---------------------------------------------------

$branch = Capture git @('rev-parse', '--abbrev-ref', 'HEAD')
if ($branch -ne 'main' -and -not $AllowBranch) { Die "On branch '$branch': release from main (or pass -AllowBranch)" }
if (Capture git @('status', '--porcelain')) { Die 'Working tree is dirty: commit or stash first' }

Run git @('fetch', 'origin', '--tags', '--quiet')
if (Capture git @('tag', '--list', $tag)) { Die "Tag $tag already exists" }
$remoteBranch = Capture git @('ls-remote', '--heads', 'origin', $branch)
if ($remoteBranch) {
    $behind = Capture git @('rev-list', '--count', "HEAD..origin/$branch")
    if ($behind -ne '0') { Die "Local $branch is $behind commit(s) behind origin: pull first" }
}

# A release must be newer than every one before it, or no installed copy would take it.
$tags = Capture git @('tag', '--list', 'v*')
$published = @($tags -split "`r?`n" | ForEach-Object { Parse-Version $_ } | Where-Object { $_ })
$newest = $published | Sort-Object -Descending | Select-Object -First 1
if ($newest -and $wanted -le $newest) { Die "$tag is not newer than the latest release, v$newest" }

& gh auth status *> $null
if ($LASTEXITCODE -ne 0) { Die 'gh is not authenticated: run gh auth login' }

$signing = Join-Path $env:USERPROFILE '.android\weblauncher-release.properties'
if (-not (Test-Path $signing)) { Die "No release key: $signing is missing. Without the original key no installed copy will accept an update." }
$signingProps = ConvertFrom-StringData ([System.IO.File]::ReadAllText($signing))
$keystore = Join-Path (Split-Path $signing) $signingProps.storeFile

# --- 3. Tools --------------------------------------------------------------

if (-not $env:JAVA_HOME) {
    $jdk = 'C:\Program Files\Android\openjdk\jdk-21.0.8'
    if (-not (Test-Path $jdk)) { Die 'JAVA_HOME is not set' }
    $env:JAVA_HOME = $jdk
}

$sdk = $env:ANDROID_HOME
if (Test-Path 'local.properties') {
    $line = Get-Content 'local.properties' | Where-Object { $_ -match '^sdk\.dir=' } | Select-Object -First 1
    if ($line) { $sdk = ($line -replace '^sdk\.dir=', '') -replace '\\:', ':' -replace '\\\\', '\' }
}
if (-not $sdk) { Die 'Android SDK not found: set sdk.dir in local.properties or ANDROID_HOME' }
$buildTools = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory | Sort-Object { [version]($_.Name -replace '[^0-9.].*$', '') } -Descending | Select-Object -First 1
$apksigner = Join-Path $buildTools.FullName 'apksigner.bat'
if (-not (Test-Path $apksigner)) { Die "apksigner not found in $($buildTools.FullName)" }

# --- 4. Version and build --------------------------------------------------

$propsPath = Join-Path $root 'gradle.properties'
$propsBefore = [System.IO.File]::ReadAllText($propsPath)
if ($propsBefore -notmatch '(?m)^weblauncher\.version=.*$') { Die 'gradle.properties has no weblauncher.version' }
$propsAfter = $propsBefore -replace '(?m)^weblauncher\.version=.*?(\r?)$', "weblauncher.version=$Version`$1"
Write-Utf8 $propsPath $propsAfter

# Die exits from inside the try, so the version is put back in finally unless the build got through.
$built = $false
try {
    Say 'testing and building'
    Run (Join-Path $root 'gradlew.bat') @('test', 'assembleRelease', '--quiet')

    $output = Join-Path $root 'app\build\outputs\apk\release\app-release.apk'
    if (-not (Test-Path $output)) { Die "Expected APK missing: $output" }

    # The APK must carry the release key, the one every installed copy was signed with.
    $apkCert = Capture $apksigner @('verify', '--print-certs', $output)
    $apkDigest = ([regex]::Match($apkCert, 'SHA-256 digest: ([0-9a-fA-F]+)')).Groups[1].Value.ToLower()
    $keyList = Capture (Join-Path $env:JAVA_HOME 'bin\keytool.exe') @('-list', '-v', '-keystore', $keystore, '-alias', $signingProps.keyAlias, '-storepass', $signingProps.storePassword)
    $keyDigest = ([regex]::Match($keyList, 'SHA256: ([0-9A-Fa-f:]+)')).Groups[1].Value.Replace(':', '').ToLower()
    if (-not $apkDigest -or $apkDigest -ne $keyDigest) { Die "The APK is not signed with the release key (APK '$apkDigest', key '$keyDigest')" }

    $releaseDir = Join-Path $root 'app\build\release'
    New-Item -ItemType Directory -Force $releaseDir | Out-Null
    $apk = Join-Path $releaseDir "weblauncher-$Version.apk"
    Copy-Item $output $apk -Force
    Say "built and verified $apk"
    $built = $true
} finally {
    if ($DryRun -or -not $built) { Write-Utf8 $propsPath $propsBefore }
}

if ($DryRun) {
    Say 'dry run OK: nothing committed, tagged or published'
    Write-Host $Notes
    exit 0
}

# --- 5. Commit, tag, push, publish -----------------------------------------

if ($propsAfter -ne $propsBefore) {
    Run git @('add', 'gradle.properties')
    Run git @('commit', '--quiet', '-m', "Release $tag")
}
$notesPath = Join-Path $releaseDir 'release-notes.txt'
Write-Utf8 $notesPath $Notes
# --cleanup=verbatim: otherwise git strips lines starting with '#', markdown headings included.
Run git @('tag', '-a', $tag, '-F', $notesPath, '--cleanup=verbatim')
Run git @('push', '--quiet', 'origin', $branch)
Run git @('push', '--quiet', 'origin', $tag)

& gh release create $tag $apk --title $tag --notes-from-tag
if ($LASTEXITCODE -ne 0) {
    Die ("gh release create failed. The tag $tag is already pushed; retry with:`n" +
        "  gh release create $tag `"$apk`" --title $tag --notes-from-tag`n" +
        "or remove the tag: git tag -d $tag; git push origin :refs/tags/$tag")
}

Say "$tag published"
