param([string]$ReportPath='target/p6-model-evaluation.json',[string]$CaseFilter='.*',[string]$RagUrl='http://localhost:8080')
$ErrorActionPreference='Stop'
$repoRoot=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$backendPath=Join-Path $repoRoot 'backend'
$envFile=Join-Path $repoRoot '.env'
if(Test-Path -LiteralPath $envFile){
    foreach($line in Get-Content -LiteralPath $envFile){
        if($line -match '^\s*(DEEPSEEK_API_KEY|DEEPSEEK_BASE_URL|DEEPSEEK_MODEL_NAME)\s*=\s*(.*)$'){
            $value=$Matches[2].Trim().Trim('"').Trim("'")
            switch($Matches[1]){'DEEPSEEK_API_KEY'{$env:P6_MODEL_KEY=$value};'DEEPSEEK_BASE_URL'{$env:P6_MODEL_BASE_URL=$value};'DEEPSEEK_MODEL_NAME'{$env:P6_MODEL_NAME=$value}}
        }
    }
}
if(-not $env:P6_MODEL_KEY){throw 'Configure DEEPSEEK_API_KEY in local .env, or P6_MODEL_KEY in this process.'}
$env:P6_REPORT_PATH=$ReportPath;$env:P6_CASE_FILTER=$CaseFilter;$env:P6_RAG_URL=$RagUrl
try{Push-Location $backendPath; & mvn '-Dtest=P6ModelEvaluationTest' test; if($LASTEXITCODE -ne 0){throw 'Evaluation failed; inspect the report, not credential values.'}}
finally{Pop-Location;Remove-Item Env:P6_MODEL_KEY -ErrorAction SilentlyContinue}
