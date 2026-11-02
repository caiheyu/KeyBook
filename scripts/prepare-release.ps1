[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,
    [string]$ReleaseNotes = 'KeyBook release',
    [string]$BuildToolsDirectory = 'C:\develop\android\Sdk\build-tools\36.0.0',
    [string]$OutputDirectory
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path (Split-Path $PSScriptRoot -Parent) 'app\build\outputs\github-release'
}

function Get-CodePointCount {
    param([string]$Value)
    $count = 0
    for ($index = 0; $index -lt $Value.Length; $index++) {
        $character = $Value[$index]
        if ([char]::IsHighSurrogate($character)) {
            if ($index + 1 -ge $Value.Length -or -not [char]::IsLowSurrogate($Value[$index + 1])) {
                throw 'Release notes contain malformed Unicode.'
            }
            $index++
        } elseif ([char]::IsLowSurrogate($character)) {
            throw 'Release notes contain malformed Unicode.'
        }
        $count++
    }
    return $count
}

function Invoke-AndroidTool {
    param([string]$Tool, [string[]]$Arguments)
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $lines = @(& $Tool @Arguments 2>&1)
        $toolExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    $output = ($lines | ForEach-Object { $_.ToString() }) -join "`n"
    if ($toolExitCode -ne 0) { throw "$Tool failed ($toolExitCode):`n$output" }
    return $output
}

$sourceApk = (Resolve-Path -LiteralPath $ApkPath).Path
if ([IO.Path]::GetExtension($sourceApk) -ne '.apk') { throw 'ApkPath must point to an APK file.' }
if ((Get-Item -LiteralPath $sourceApk).Length -gt 256MB) { throw 'The APK exceeds KeyBook updater limit of 256 MB.' }

$aapt = Join-Path $BuildToolsDirectory 'aapt.exe'
$apksigner = Join-Path $BuildToolsDirectory 'apksigner.bat'
foreach ($tool in @($aapt, $apksigner)) {
    if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
        throw "Missing Android SDK tool: $tool. Pass -BuildToolsDirectory with your SDK build-tools folder."
    }
}

$badging = Invoke-AndroidTool -Tool $aapt -Arguments @('dump', 'badging', $sourceApk)
$package = [regex]::Match($badging, "(?m)^package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'")
$sdk = [regex]::Match($badging, "(?m)^sdkVersion:'([0-9]+)'")
if (-not $package.Success -or -not $sdk.Success) { throw 'Cannot read APK package, version, or minimum SDK.' }
if ($package.Groups[1].Value -ne 'com.github.caiheyu.keybook') { throw 'This APK is not KeyBook.' }
if ($badging -match '(?m)^application-debuggable\s*$') {
    throw 'Debug APKs cannot be released. Generate a signed release APK first.'
}

$versionCode = [long]$package.Groups[2].Value
$versionName = $package.Groups[3].Value
$minSdk = [int]$sdk.Groups[1].Value
if ($versionCode -lt 1 -or $versionCode -gt [int]::MaxValue -or
    $minSdk -lt 26 -or $minSdk -gt 100 -or
    (Get-CodePointCount $versionName) -notin 1..40 -or
    (Get-CodePointCount $ReleaseNotes) -gt 4000) {
    throw 'APK metadata or release notes are outside KeyBook updater limits.'
}

$signature = Invoke-AndroidTool -Tool $apksigner -Arguments @('verify', '--verbose', '--print-certs', $sourceApk)
if ($signature -notmatch '(?m)^Verifies\s*$') { throw 'APK signature verification did not report Verifies.' }
$certificates = [regex]::Matches($signature, '(?m)^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$')
if ($certificates.Count -ne 1) { throw 'KeyBook updater requires exactly one signing certificate.' }
if ($signature -match '(?im)^Signer #[0-9]+ certificate DN:.*CN=Android Debug(?:,|\s*$)') {
    throw 'An Android debug signing key cannot be used for a public release.'
}
$certificateSha256 = $certificates[0].Groups[1].Value.ToLowerInvariant()

New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$releaseDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$releaseApk = Join-Path $releaseDirectory 'keybook.apk'
if (-not [string]::Equals($sourceApk, $releaseApk, [StringComparison]::OrdinalIgnoreCase)) {
    Copy-Item -LiteralPath $sourceApk -Destination $releaseApk -Force
}

$manifest = [ordered]@{
    schemaVersion = 1
    versionName = $versionName
    versionCode = $versionCode
    minSdk = $minSdk
    apkSha256 = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant()
    signerCertificateSha256 = $certificateSha256
    publishedAt = [DateTime]::UtcNow.ToString("yyyy-MM-dd'T'HH:mm:ss'Z'")
    releaseNotes = $ReleaseNotes
}
$manifestPath = Join-Path $releaseDirectory 'update.json'
[IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json), [Text.UTF8Encoding]::new($false))

Write-Host "Prepared KeyBook $versionName (versionCode $versionCode)."
Write-Host 'Upload both files to the same public, non-prerelease GitHub Release:'
Write-Host "  $releaseApk"
Write-Host "  $manifestPath"
Write-Host 'No signing key or password was read; no files were uploaded.'
