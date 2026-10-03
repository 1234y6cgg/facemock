param([ValidateSet('Live','Unavailable')][string]$ModelMode='Live')
$ErrorActionPreference='Stop'
$p2Backend=Resolve-Path "$PSScriptRoot/../../.."
$p2Repo=Resolve-Path "$p2Backend/.."
foreach($p2Entry in (Get-Content "$p2Repo/.env" | Where-Object { $_ -match '^(DEEPSEEK_API_KEY|DEEPSEEK_BASE_URL|DEEPSEEK_MODEL_NAME)=' })) {
    $p2Parts=$p2Entry.Split('=',2)
    [Environment]::SetEnvironmentVariable($p2Parts[0],$p2Parts[1].Trim().Trim('"').Trim("'"),'Process')
}
if($ModelMode -eq 'Unavailable') { $env:DEEPSEEK_BASE_URL='http://127.0.0.1:9' }
$env:MYSQL_HOST='127.0.0.1'; $env:MYSQL_PORT='13316'; $env:MYSQL_DATABASE='p2_acceptance'
$env:MYSQL_USER='root'; $env:MYSQL_PASSWORD='p2-test-only'
$env:REDIS_HOST='127.0.0.1'; $env:REDIS_PORT='16379'
$env:CHROMA_BASE_URL='http://127.0.0.1:18000'; $env:KNOWLEDGE_COLLECTION='p2_http_acceptance'
$p2Jar=Join-Path $p2Backend 'target/facemock-backend-0.1.0.jar'
$p2Process=Start-Process -FilePath 'D:\Java\JDK17\bin\java.exe' -ArgumentList @('-jar',('"' + $p2Jar + '"'),'--server.port=18080') -WindowStyle Hidden -PassThru -WorkingDirectory $p2Backend -RedirectStandardOutput "$p2Backend/target/p2-smoke.stdout.log" -RedirectStandardError "$p2Backend/target/p2-smoke.stderr.log"
$p2Process.Id | Set-Content "$p2Backend/target/p2-acceptance.pid"
"P2 acceptance application PID: $($p2Process.Id); mode: $ModelMode"

