param([ValidateSet('Live','Unavailable')][string]$ModelMode='Live')
$ErrorActionPreference='Stop'
$backendPath=(Resolve-Path "$PSScriptRoot/../../..").Path
$repoPath=(Resolve-Path "$backendPath/..").Path
foreach($entry in (Get-Content "$repoPath/.env" | Where-Object {$_ -match '^(DEEPSEEK_API_KEY|DEEPSEEK_BASE_URL|DEEPSEEK_MODEL_NAME)='})){
    $parts=$entry.Split('=',2);[Environment]::SetEnvironmentVariable($parts[0],$parts[1].Trim().Trim('"').Trim("'"),'Process')
}
if($ModelMode -eq 'Unavailable'){$env:DEEPSEEK_BASE_URL='http://127.0.0.1:9'}
$env:MYSQL_HOST='127.0.0.1';$env:MYSQL_PORT='13319';$env:MYSQL_DATABASE='p5check';$env:MYSQL_USER='root';$env:MYSQL_PASSWORD='p5-local-check'
$env:REDIS_HOST='127.0.0.1';$env:REDIS_PORT='16379';$env:CHROMA_BASE_URL='http://127.0.0.1:18003';$env:KNOWLEDGE_COLLECTION='p5_acceptance_bge'
$env:SPEECH_ENABLED='false';$env:SPEECH_STORAGE_PATH=Join-Path $backendPath 'target/p5-recordings'
$jarPath=Join-Path $backendPath 'target/facemock-backend-0.1.0.jar'
$process=Start-Process -FilePath 'D:\Java\JDK17\bin\java.exe' -ArgumentList @('-jar',('"'+$jarPath+'"'),'--server.port=18083') -WindowStyle Hidden -PassThru -WorkingDirectory $backendPath -RedirectStandardOutput "$backendPath/target/p5-app.stdout.log" -RedirectStandardError "$backendPath/target/p5-app.stderr.log"
$process.Id|Set-Content "$backendPath/target/p5-acceptance.pid"
"P5 acceptance application PID: $($process.Id); model mode: $ModelMode"
