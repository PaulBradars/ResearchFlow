param([string]$Destination = 'target/desktop')
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
Push-Location $workspace
try {
    & .\mvnw.cmd -B -ntp -Pdesktop clean package
    if ($LASTEXITCODE -ne 0) { throw 'Desktop build failed.' }
    & jpackage --type app-image --name ResearchFlow --app-version 0.1.0 --input target/package-input --main-jar researchflow-ai-0.1.0-SNAPSHOT.jar --main-class researchflow.app.Launcher --dest $Destination --add-modules java.se,jdk.unsupported --java-options '--enable-native-access=ALL-UNNAMED' --java-options '-Dresearchflow.seed=false'
    if ($LASTEXITCODE -ne 0) { throw 'Runtime packaging failed. Use a JDK 25+ with jpackage and a destination without an existing ResearchFlow image.' }
} finally { Pop-Location }
