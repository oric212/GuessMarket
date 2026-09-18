$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
& (Join-Path $root 'build-server.ps1')

$buildDirectory = Join-Path $root 'server-build'
$dependencyDirectory = Join-Path $root '.deps'
$testClasses = Join-Path $buildDirectory 'server-test-classes'
New-Item -ItemType Directory -Force -Path $testClasses | Out-Null

$sources = @(Get-ChildItem -Recurse -File (Join-Path $root 'guessmarket-server/test') -Filter '*.java' | ForEach-Object FullName)
$classpath = (Join-Path $buildDirectory 'core-classes') + [IO.Path]::PathSeparator +
        (Join-Path $buildDirectory 'server-classes') + [IO.Path]::PathSeparator +
        (Join-Path $dependencyDirectory '*')
& javac -encoding UTF-8 -cp $classpath -d $testClasses $sources
if ($LASTEXITCODE -ne 0) { throw 'Server test compilation failed' }

$runtimeClasspath = $testClasses + [IO.Path]::PathSeparator + $classpath
& java -cp $runtimeClasspath guessmarket.server.ServerFoundationTest
if ($LASTEXITCODE -ne 0) { throw 'Server tests failed' }
