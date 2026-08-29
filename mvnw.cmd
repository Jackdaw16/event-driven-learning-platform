<# : batch portion
@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements. See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership. The ASF licenses this file
@REM to you under the Apache License, Version 2.0.
@REM ----------------------------------------------------------------------------

@IF "%__MVNW_ARG0_NAME__%"=="" (SET __MVNW_ARG0_NAME__=%~nx0)
@SET __MVNW_CMD__=
@SET __MVNW_PSMODULEP_SAVE=%PSModulePath%
@SET PSModulePath=
@FOR /F "usebackq tokens=1* delims==" %%A IN (`powershell -noprofile "& {$scriptDir='%~dp0'; $script='%__MVNW_ARG0_NAME__%'; icm -ScriptBlock ([Scriptblock]::Create((Get-Content -Raw '%~f0'))) -NoNewScope}"`) DO @(
  IF "%%A"=="MVN_CMD" (set __MVNW_CMD__=%%B) ELSE IF "%%B"=="" (echo %%A) ELSE (echo %%A=%%B)
)
@SET PSModulePath=%__MVNW_PSMODULEP_SAVE%
@SET __MVNW_PSMODULEP_SAVE=
@SET __MVNW_ARG0_NAME__=
@IF NOT "%__MVNW_CMD__%"=="" ("%__MVNW_CMD__%" %*)
@echo Cannot start maven from wrapper >&2 && exit /b 1
@GOTO :EOF
: end batch / begin powershell #>

$ErrorActionPreference = "Stop"
if ($env:MVNW_VERBOSE -eq "true") { $VerbosePreference = "Continue" }

$properties = ConvertFrom-StringData (Get-Content -Raw "$scriptDir/.mvn/wrapper/maven-wrapper.properties")
$distributionUrl = $properties.distributionUrl
if (!$distributionUrl) { Write-Error "cannot read distributionUrl property" }

$distributionUrlName = $distributionUrl -replace '^.*/',''
$distributionUrlNameMain = $distributionUrlName -replace '\.[^.]*$','' -replace '-bin$',''
$MAVEN_M2_PATH = if ($env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME } else { "$HOME/.m2" }
if (-not (Test-Path $MAVEN_M2_PATH)) { New-Item -Path $MAVEN_M2_PATH -ItemType Directory | Out-Null }
$MAVEN_WRAPPER_DISTS = "$MAVEN_M2_PATH/wrapper/dists"
$MAVEN_HOME_NAME = ([System.Security.Cryptography.SHA256]::Create().ComputeHash([byte[]][char[]]$distributionUrl) | ForEach-Object {$_.ToString("x2")}) -join ''
$MAVEN_HOME_PARENT = "$MAVEN_WRAPPER_DISTS/$distributionUrlNameMain"
$MAVEN_HOME = "$MAVEN_HOME_PARENT/$MAVEN_HOME_NAME"
$MVN_CMD = $script -replace '^mvnw','mvn'

if (!(Test-Path "$MAVEN_HOME/bin/$MVN_CMD" -PathType Leaf)) {
  $tmpHolder = New-TemporaryFile
  $tmp = New-Item -ItemType Directory -Path "$tmpHolder.dir"
  $tmpHolder.Delete()
  try {
    New-Item -ItemType Directory -Path $MAVEN_HOME_PARENT -Force | Out-Null
    $zip = "$tmp/$distributionUrlName"
    Write-Verbose "Downloading Maven from $distributionUrl"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    (New-Object System.Net.WebClient).DownloadFile($distributionUrl, $zip)
    Expand-Archive $zip -DestinationPath $tmp | Out-Null
    $extracted = Join-Path $tmp $distributionUrlNameMain
    if (!(Test-Path $extracted -PathType Container)) { Write-Error "Could not find Maven distribution directory" }
    Rename-Item -Path $extracted -NewName $MAVEN_HOME_NAME | Out-Null
    Move-Item -Path "$tmp/$MAVEN_HOME_NAME" -Destination $MAVEN_HOME_PARENT | Out-Null
  }
  finally {
    if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue }
  }
}

Write-Output "MVN_CMD=$MAVEN_HOME/bin/$MVN_CMD"
