[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$InputZip,
    [Parameter(Mandatory = $true)][string]$OutputZip,
    [Parameter(Mandatory = $true)][string]$ModId,
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][ValidateRange(1, 2147483647)][long]$VersionCode,
    [Parameter(Mandatory = $true)][string]$ManifestUrl,
    [Parameter(Mandatory = $true)][string]$ZipUrl,
    [Parameter(Mandatory = $true)][string]$ManifestOutput,
    [string]$Changelog = ''
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$utf8 = New-Object System.Text.UTF8Encoding($false)
if ($ModId.Length -gt 120 -or $ModId -notmatch '^[a-z0-9]+(?:[._-][a-z0-9]+)*$' -or $ModId.Length -lt 3) { throw 'Invalid modId.' }
if ([string]::IsNullOrWhiteSpace($Version) -or $Version.Length -gt 40) { throw 'Version must contain 1-40 characters.' }
if ($Changelog.Length -gt 2000) { throw 'Changelog is limited to 2000 characters.' }
foreach ($address in @($ManifestUrl, $ZipUrl)) {
    $uri = [Uri]$address
    if (-not $uri.IsAbsoluteUri -or $uri.Scheme -ne 'https' -or $uri.UserInfo -or $uri.Fragment -or $uri.Port -ne 443) {
        throw 'Public HTTPS URLs without credentials or fragments are required.'
    }
}
$sourcePath = (Resolve-Path -LiteralPath $InputZip).Path
$outputPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputZip)
$manifestPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ManifestOutput)
if ($sourcePath -eq $outputPath -or $sourcePath -eq $manifestPath -or $outputPath -eq $manifestPath) { throw 'Input and output paths must differ.' }
foreach ($destination in @($outputPath, $manifestPath)) {
    if (Test-Path -LiteralPath $destination) { throw "Output already exists: $destination" }
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination)) | Out-Null
}
if ((Get-Item -LiteralPath $sourcePath).Length -gt 1GB) { throw 'Input ZIP exceeds 1 GiB.' }
$temporaryPath = $outputPath + '.' + [Guid]::NewGuid().ToString('N') + '.part'
$inputArchive = [IO.Compression.ZipFile]::OpenRead($sourcePath)
try {
    $entryNames = @($inputArchive.Entries | ForEach-Object { $_.FullName.ToLowerInvariant() })
    if (($entryNames | Select-Object -Unique).Count -ne $entryNames.Count) { throw 'Duplicate ZIP entries.' }
    $infoEntry = $inputArchive.GetEntry('info.json')
    if ($null -eq $infoEntry -or $infoEntry.Length -gt 32KB) { throw 'A root info.json, at most 32 KiB, is required.' }
    $reader = New-Object IO.StreamReader($infoEntry.Open(), $utf8)
    try { $info = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
    $info | Add-Member -NotePropertyName modId -NotePropertyValue $ModId -Force
    $info | Add-Member -NotePropertyName versionCode -NotePropertyValue $VersionCode -Force
    $info | Add-Member -NotePropertyName version -NotePropertyValue $Version -Force
    $info | Add-Member -NotePropertyName schemaVersion -NotePropertyValue 1 -Force
    $source = [ordered]@{ schemaVersion = 1; modId = $ModId; manifestUrl = $ManifestUrl }
    $infoJson = $info | ConvertTo-Json -Depth 10
    $sourceJson = $source | ConvertTo-Json
    if ($utf8.GetByteCount($infoJson) -gt 32KB) { throw 'info.json exceeds 32 KiB.' }
    $entries = @($inputArchive.Entries | Where-Object { $_.FullName -notin @('info.json', 'update.json') })
    if ($entries.Count + 2 -gt 200) { throw 'Output would exceed 200 ZIP entries.' }
    $total = 0L
    foreach ($entry in $entries) {
        if ($entry.Length -gt 512MB) { throw 'A file exceeds 512 MiB.' }
        $total += $entry.Length
    }
    if ($total -gt 2GB) { throw 'Expanded content exceeds 2 GiB.' }
    $outputArchive = [IO.Compression.ZipFile]::Open($temporaryPath, [IO.Compression.ZipArchiveMode]::Create)
    try {
        foreach ($entry in $entries) {
            $copy = $outputArchive.CreateEntry($entry.FullName, [IO.Compression.CompressionLevel]::Optimal)
            $copy.ExternalAttributes = $entry.ExternalAttributes
            $inputStream = $entry.Open(); $outputStream = $copy.Open()
            try { $inputStream.CopyTo($outputStream, 65536) } finally { $outputStream.Dispose(); $inputStream.Dispose() }
        }
        foreach ($document in @(@{ name = 'info.json'; json = $infoJson }, @{ name = 'update.json'; json = $sourceJson })) {
            $entry = $outputArchive.CreateEntry($document.name)
            $writer = New-Object IO.StreamWriter($entry.Open(), $utf8)
            try { $writer.Write($document.json) } finally { $writer.Dispose() }
        }
    } finally { $outputArchive.Dispose() }
    $zipSize = (Get-Item -LiteralPath $temporaryPath).Length
    if ($zipSize -gt 1GB) { throw 'Output ZIP exceeds 1 GiB.' }
    $hash = (Get-FileHash -LiteralPath $temporaryPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $release = [ordered]@{ schemaVersion = 1; modId = $ModId; version = $Version; versionCode = $VersionCode;
        zipUrl = $ZipUrl; zipSha256 = $hash; zipSize = $zipSize; changelog = $Changelog }
    $manifestJson = $release | ConvertTo-Json
    if ($utf8.GetByteCount($manifestJson) -gt 32KB) { throw 'Manifest exceeds 32 KiB.' }
    [IO.File]::WriteAllText($manifestPath, $manifestJson, $utf8)
    Move-Item -LiteralPath $temporaryPath -Destination $outputPath
    [pscustomobject]@{ Zip = $outputPath; Manifest = $manifestPath; SHA256 = $hash; Bytes = $zipSize }
} finally {
    $inputArchive.Dispose()
    if (Test-Path -LiteralPath $temporaryPath) { Remove-Item -LiteralPath $temporaryPath }
}
