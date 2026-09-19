$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$build = Join-Path $root 'server-build'
$core = Join-Path $build 'core-classes'
$classes = Join-Path $build 'javafx-classes'
$tests = Join-Path $build 'javafx-test-classes'
$dependencies = Join-Path $root '.deps'
$javafx = if ($env:JAVAFX_SDK) { Join-Path $env:JAVAFX_SDK 'lib' } else { 'C:\tools\javafx-sdk-25\lib' }

& (Join-Path $root 'build-server.ps1')
New-Item -ItemType Directory -Force -Path $classes, $tests | Out-Null
$classpath = $core + [IO.Path]::PathSeparator + (Join-Path $dependencies '*') +
        [IO.Path]::PathSeparator + (Join-Path $javafx '*')
$sources = @(Get-ChildItem -Recurse -File (Join-Path $root 'guessmarket-javafx/src') -Filter '*.java' |
        ForEach-Object FullName)
& javac -encoding UTF-8 -cp $classpath -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'JavaFX compilation failed' }
$viewTarget = Join-Path $classes 'guessmarket/javafx/view'
New-Item -ItemType Directory -Force -Path $viewTarget | Out-Null
Copy-Item -Force (Join-Path $root 'guessmarket-javafx/src/guessmarket/javafx/view/*.css') $viewTarget

$testSources = @(Get-ChildItem -Recurse -File (Join-Path $root 'guessmarket-javafx/test') -Filter '*.java' |
        ForEach-Object FullName)
$testClasspath = $tests + [IO.Path]::PathSeparator + $classes + [IO.Path]::PathSeparator + $classpath
& javac --add-modules jdk.httpserver -encoding UTF-8 -cp $testClasspath -d $tests $testSources
if ($LASTEXITCODE -ne 0) { throw 'JavaFX test compilation failed' }

foreach ($testClass in @(
    'guessmarket.javafx.client.GuessMarketApiClientTest',
    'guessmarket.javafx.client.SynchronizationServiceTest',
    'guessmarket.javafx.controller.CreateEventWorkflowTest',
    'guessmarket.javafx.controller.EventsControllerFilterTest',
    'guessmarket.javafx.controller.UsersControllerWorkflowTest',
    'guessmarket.javafx.view.AnimationSettingsTest',
    'guessmarket.javafx.view.LmsrValueFormatterTest',
    'guessmarket.javafx.view.SkinThemeTest'
)) {
    & java --add-modules jdk.httpserver -cp $testClasspath $testClass
    if ($LASTEXITCODE -ne 0) { throw "JavaFX test failed: $testClass" }
}
