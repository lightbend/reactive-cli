$ErrorActionPreference = "Stop"

$version = $args[0]

git checkout "tags/v$version"

$git_success = $?

if (-not $git_success) {
  throw "Invalid version specified. Specify version as first argument to script"
}

$env:JAVA_OPTS = "-Xmx4G"

# FIXME should be running tests, not doing so right now because of line ending issues
sbt clean cliJS/package

$sbt_success = $?

if (-not $sbt_success) {
  throw "sbt failed"
}

mkdir target\win

nexe -o target\win\rp.exe cli\js\target\rp.js

$nexe_success = $?

if (-not $nexe_success) {
  throw "nexe failed"
}

Compress-Archive -Path .\target\win\rp.exe -DestinationPath ".\target\win\reactive-cli-$version-Windows-amd64.zip"