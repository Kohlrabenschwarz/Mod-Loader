[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [string]$OutputDirectory = '.\update-fixtures',
    [string]$InputZip = ''
)
$ErrorActionPreference = 'Stop'
$base = [Uri]($BaseUrl.TrimEnd('/') + '/')
if (-not $base.IsAbsoluteUri -or $base.Scheme -ne 'https' -or $base.Query -or $base.Fragment -or $base.UserInfo) {
    throw 'BaseUrl must be a public HTTPS directory URL without a query, credentials or fragment.'
}
if ([string]::IsNullOrWhiteSpace($InputZip)) {
    $InputZip = Join-Path $PSScriptRoot 'generic-mod-template.zip'
    if (-not (Test-Path -LiteralPath $InputZip)) { $InputZip = Join-Path $PSScriptRoot '..\examples\generic-mod-template.zip' }
}
$packager = Join-Path $PSScriptRoot 'New-ModUpdatePackage.ps1'
$manifestUrl = [Uri]::new($base, 'latest.json').AbsoluteUri
& $packager -InputZip $InputZip -OutputZip (Join-Path $OutputDirectory 'sample-mod-v1.zip') `
    -ModId com.example.update-test -Version 1.0.0 -VersionCode 1 -ManifestUrl $manifestUrl `
    -ZipUrl ([Uri]::new($base, 'sample-mod-v1.zip').AbsoluteUri) -ManifestOutput (Join-Path $OutputDirectory 'v1-manifest.json')
& $packager -InputZip $InputZip -OutputZip (Join-Path $OutputDirectory 'sample-mod-v2.zip') `
    -ModId com.example.update-test -Version 1.1.0 -VersionCode 2 -ManifestUrl $manifestUrl `
    -ZipUrl ([Uri]::new($base, 'sample-mod-v2.zip').AbsoluteUri) -ManifestOutput (Join-Path $OutputDirectory 'latest.json') `
    -Changelog 'Update pipeline test; dummy bundles, do not activate in the real game.'
Write-Output 'Upload sample-mod-v2.zip and latest.json to BaseUrl. Import sample-mod-v1.zip, then check its card for an update.'
Write-Output 'Default template bundles are dummy fixtures: do not activate them in the real game.'
