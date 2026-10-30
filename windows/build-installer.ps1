# Builds windows\dist\SparkGuard-Setup.exe. Needs the .NET 8 SDK and Inno Setup 6. It only builds the installer: it does not install or start anything.
$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$dotnet = if (Test-Path "$env:LOCALAPPDATA\dotnet-sdk\dotnet.exe") { "$env:LOCALAPPDATA\dotnet-sdk\dotnet.exe" } else { 'dotnet' }
$iscc = @("$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe", "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe", "$env:ProgramFiles\Inno Setup 6\ISCC.exe") | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $iscc) { throw 'Inno Setup 6 was not found.' }

& $dotnet test "$here\SparkGuard.Tests" --nologo -v q
if ($LASTEXITCODE -ne 0) { throw 'The tests failed, so no installer was built.' }

Remove-Item -Recurse -Force "$here\publish" -ErrorAction SilentlyContinue
& $dotnet publish "$here\SparkGuard.Service" -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -o "$here\publish" --nologo -v q
if ($LASTEXITCODE -ne 0) { throw 'publish failed' }

& $iscc "$here\installer\SparkGuard.iss"
if ($LASTEXITCODE -ne 0) { throw 'the installer did not build' }
Get-Item "$here\dist\SparkGuard-Setup.exe" | Select-Object FullName, Length
