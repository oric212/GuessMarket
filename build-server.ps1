$ErrorActionPreference = 'Stop'

$root = $PSScriptRoot
$dependencyDirectory = Join-Path $root '.deps'
$buildDirectory = Join-Path $root 'server-build'
$distributionDirectory = Join-Path $root 'server-dist'
$warStagingDirectory = Join-Path $buildDirectory 'war'

$dependencies = [ordered]@{
    'gson-2.14.0.jar' = 'https://repo1.maven.org/maven2/com/google/code/gson/gson/2.14.0/gson-2.14.0.jar'
    'jakarta.servlet-api-6.1.0.jar' = 'https://repo1.maven.org/maven2/jakarta/servlet/jakarta.servlet-api/6.1.0/jakarta.servlet-api-6.1.0.jar'
    'jakarta.xml.bind-api-4.0.2.jar' = 'https://repo1.maven.org/maven2/jakarta/xml/bind/jakarta.xml.bind-api/4.0.2/jakarta.xml.bind-api-4.0.2.jar'
    'jaxb-core-4.0.5.jar' = 'https://repo1.maven.org/maven2/org/glassfish/jaxb/jaxb-core/4.0.5/jaxb-core-4.0.5.jar'
    'jaxb-runtime-4.0.5.jar' = 'https://repo1.maven.org/maven2/org/glassfish/jaxb/jaxb-runtime/4.0.5/jaxb-runtime-4.0.5.jar'
    'jakarta.activation-api-2.1.3.jar' = 'https://repo1.maven.org/maven2/jakarta/activation/jakarta.activation-api/2.1.3/jakarta.activation-api-2.1.3.jar'
    'angus-activation-2.0.2.jar' = 'https://repo1.maven.org/maven2/org/eclipse/angus/angus-activation/2.0.2/angus-activation-2.0.2.jar'
    'istack-commons-runtime-4.1.2.jar' = 'https://repo1.maven.org/maven2/com/sun/istack/istack-commons-runtime/4.1.2/istack-commons-runtime-4.1.2.jar'
}

New-Item -ItemType Directory -Force -Path $dependencyDirectory | Out-Null
foreach ($dependency in $dependencies.GetEnumerator()) {
    $target = Join-Path $dependencyDirectory $dependency.Key
    if (-not (Test-Path -LiteralPath $target)) {
        Write-Host "Downloading $($dependency.Key)..."
        Invoke-WebRequest -UseBasicParsing -Uri $dependency.Value -OutFile $target
    }
}

foreach ($directory in @($buildDirectory, $distributionDirectory)) {
    if (Test-Path -LiteralPath $directory) {
        $resolved = [IO.Path]::GetFullPath($directory)
        if (-not $resolved.StartsWith([IO.Path]::GetFullPath($root), [StringComparison]::OrdinalIgnoreCase)) {
            throw "Refusing to clean path outside the repository: $resolved"
        }
        Remove-Item -Recurse -Force -LiteralPath $resolved
    }
}

$coreClasses = Join-Path $buildDirectory 'core-classes'
$serverClasses = Join-Path $buildDirectory 'server-classes'
$webInf = Join-Path $warStagingDirectory 'WEB-INF'
$webInfClasses = Join-Path $webInf 'classes'
$webInfLib = Join-Path $webInf 'lib'
New-Item -ItemType Directory -Force -Path $coreClasses, $serverClasses, $webInfClasses, $webInfLib, $distributionDirectory | Out-Null

$coreSources = @(Get-ChildItem -Recurse -File (Join-Path $root 'guessmarket-core/src'), (Join-Path $root 'guessmarket-core/generated') -Filter '*.java' | ForEach-Object FullName)
& javac -encoding UTF-8 -cp (Join-Path $dependencyDirectory '*') -d $coreClasses $coreSources
if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed' }
New-Item -ItemType Directory -Force -Path (Join-Path $coreClasses 'guessmarket/xml') | Out-Null
Copy-Item -Force (Join-Path $root 'schema/GM-EX3-Schema.xsd') (Join-Path $coreClasses 'guessmarket/xml/GM-EX3-Schema.xsd')

$serverSources = @(Get-ChildItem -Recurse -File (Join-Path $root 'guessmarket-server/src') -Filter '*.java' | ForEach-Object FullName)
$serverClasspath = $coreClasses + [IO.Path]::PathSeparator + (Join-Path $dependencyDirectory '*')
& javac -encoding UTF-8 -cp $serverClasspath -d $serverClasses $serverSources
if ($LASTEXITCODE -ne 0) { throw 'Server compilation failed' }

Copy-Item -Recurse -Force (Join-Path $coreClasses '*') $webInfClasses
Copy-Item -Recurse -Force (Join-Path $serverClasses '*') $webInfClasses
Copy-Item -Force (Join-Path $root 'guessmarket-server/WEB-INF/web.xml') $webInf

$runtimeJars = @(
    'gson-2.14.0.jar',
    'jakarta.xml.bind-api-4.0.2.jar',
    'jaxb-core-4.0.5.jar',
    'jaxb-runtime-4.0.5.jar',
    'jakarta.activation-api-2.1.3.jar',
    'angus-activation-2.0.2.jar',
    'istack-commons-runtime-4.1.2.jar'
)
foreach ($jarName in $runtimeJars) {
    Copy-Item -Force (Join-Path $dependencyDirectory $jarName) $webInfLib
}

$warPath = Join-Path $distributionDirectory 'GuessMarket.war'
& jar --create --file $warPath -C $warStagingDirectory .
if ($LASTEXITCODE -ne 0) { throw 'WAR packaging failed' }
Write-Host "Built $warPath"
