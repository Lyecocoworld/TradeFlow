Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarPath = 'C:\Users\space\.gradle\caches\modules-2\files-2.1\fr.maxlego08.menu\zmenu-api\1.1.1.2\44a4b0159ab0cda1522aba3d94aa0eb5d89269fb\zmenu-api-1.1.1.2.jar'
$zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
foreach ($entry in $zip.Entries) {
    Write-output $entry.FullName
}
$zip.Dispose()
