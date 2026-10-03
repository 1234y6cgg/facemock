param([Parameter(Mandatory=$true)][string]$WavPath)
$ErrorActionPreference='Stop'
$backendPath=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../..'))
$projectPath=Split-Path $backendPath -Parent
$recordingPath=(Resolve-Path -LiteralPath $WavPath).Path
$names=@('P3_ASR_TEST_WAV','P3_ASR_APP_ID','P3_ASR_API_KEY','P3_ASR_API_SECRET')
$previous=@{}
foreach($name in $names){$previous[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
try {
    $settings=@{}
    foreach($line in Get-Content -LiteralPath (Join-Path $projectPath '.env')) {
        if($line -match '^\s*(XFYUN_APP_ID|XFYUN_API_KEY|XFYUN_API_SECRET)\s*=(.*)$') {
            $settings[$Matches[1]]=$Matches[2].Trim().Trim('"').Trim("'")
        }
    }
    foreach($name in @('APP_ID','API_KEY','API_SECRET')) {
        if([string]::IsNullOrWhiteSpace($settings['XFYUN_'+$name])){throw '请先在本地 .env 配置三项 IAT 凭证。'}
        [Environment]::SetEnvironmentVariable('P3_ASR_'+$name,$settings['XFYUN_'+$name],'Process')
    }
    $env:P3_ASR_TEST_WAV=$recordingPath
    Write-Output '将此真实 WAV 上传至科大讯飞 IAT；可能消耗控制台额度。报告只保存摘要，不输出转写或密钥。'
    Push-Location $backendPath
    try { & mvn -Dtest=XfyunIatIntegrationTest test; if($LASTEXITCODE -ne 0){throw '真实 ASR 检查未通过。'} }
    finally { Pop-Location }
} finally { foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$previous[$name],'Process')} }
