param(
  [Parameter(Mandatory=$true)]
  [string]$SavedResultsDir,

  [string]$LlmBaseUrl = "http://localhost:8080",
  [string]$Model = "",
  [int]$MaxFiles = -1,
  [int]$MaxFolders = -1,
  [switch]$SkipMethods
)

$ErrorActionPreference = "Stop"

mvn -f .\h2-worker\pom.xml -DskipTests package

$jar = ".\h2-worker\target\stinger-h2-worker-1.0.0-SNAPSHOT.jar"
if (-not (Test-Path $jar)) {
  throw "Jar not found: $jar"
}

$argsList = @(
  "-jar", $jar,
  "--savedResultsDir", $SavedResultsDir,
  "--llmBaseUrl", $LlmBaseUrl
)

if ($Model -and $Model.Trim().Length -gt 0) {
  $argsList += @("--model", $Model)
}
if ($MaxFiles -ge 0) {
  $argsList += @("--maxFiles", "$MaxFiles")
}
if ($MaxFolders -ge 0) {
  $argsList += @("--maxFolders", "$MaxFolders")
}
if ($SkipMethods) {
  $argsList += "--skipMethods"
}

java @argsList
