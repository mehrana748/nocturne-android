@echo off
setlocal EnableExtensions
set GRADLE_VERSION=8.13
set GRADLE_USER_HOME_DEFAULT=%USERPROFILE%\.gradle
if "%GRADLE_USER_HOME%"=="" set GRADLE_USER_HOME=%GRADLE_USER_HOME_DEFAULT%
set GRADLE_ROOT=%GRADLE_USER_HOME%\nocturne-bootstrap
set GRADLE_HOME=%GRADLE_ROOT%\gradle-%GRADLE_VERSION%
if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  echo Downloading and verifying Gradle %GRADLE_VERSION%...
  if not exist "%GRADLE_ROOT%" mkdir "%GRADLE_ROOT%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $url='https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip'; $zip='%GRADLE_ROOT%\gradle-%GRADLE_VERSION%-bin.zip'; Invoke-WebRequest $url -OutFile $zip; $expected='20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78'; $actual=(Get-FileHash -Algorithm SHA256 $zip).Hash.ToLower(); if ($actual -ne $expected) { Remove-Item $zip -Force; throw 'Gradle SHA-256 verification failed' }; Expand-Archive -Force $zip '%GRADLE_ROOT%'"
  if errorlevel 1 exit /b 1
)
call "%GRADLE_HOME%\bin\gradle.bat" %*
