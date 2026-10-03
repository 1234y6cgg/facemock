param(
    [string]$BaseUrl = 'http://127.0.0.1:18080',
    [string]$ReportPath = 'target/p2-http-acceptance.json'
)
$ErrorActionPreference = 'Stop'
function Get-P2($Path) { Invoke-RestMethod -Uri ($BaseUrl + '/api' + $Path) }
function Post-P2($Path, $Body) {
    $p2Bytes = [System.Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 15 -Compress))
    Invoke-RestMethod -Uri ($BaseUrl + '/api' + $Path) -Method Post -ContentType 'application/json; charset=utf-8' -Body $p2Bytes
}
function Check-P2($Condition, $Message) { if (-not $Condition) { throw $Message } }
function Wait-P2($AttemptId) {
    for ($p2Try = 0; $p2Try -lt 35; $p2Try++) {
        $p2Attempt = Get-P2 "/practice/attempts/$AttemptId"
        if ($p2Attempt.evaluation.status -eq 'SUCCEEDED') { return $p2Attempt }
        if ($p2Attempt.evaluation.status -eq 'FAILED') { throw "Evaluation failed: $($p2Attempt.evaluation.errorCode)" }
        Start-Sleep -Seconds 2
    }
    throw 'Evaluation timed out in acceptance polling'
}
function Check-P2Status($Operation, $Expected) {
    try { & $Operation | Out-Null; throw "Expected HTTP $Expected" }
    catch {
        if ($null -eq $_.Exception.Response -or [int]$_.Exception.Response.StatusCode -ne $Expected) { throw }
    }
}
$p2Session = Post-P2 '/practice/sessions' @{ questionId='redis.lock'; questionVersion=1; clientRequestId=[guid]::NewGuid().ToString() }
Check-P2 ($p2Session.attempts.Count -eq 0) 'New session unexpectedly has attempts'
Check-P2Status { Get-P2 "/practice/sessions/$($p2Session.id)/reference" } 409
$p2FirstBody = @{ answer='我会用 NX 加超时时间一次设置，并放一个只有我知道的随机标记。'; inputMode='TEXT'; parentAttemptId=$null; clientRequestId=[guid]::NewGuid().ToString() }
$p2First = Post-P2 "/practice/sessions/$($p2Session.id)/attempts" $p2FirstBody
$p2Duplicate = Post-P2 "/practice/sessions/$($p2Session.id)/attempts" $p2FirstBody
Check-P2 ($p2First.id -eq $p2Duplicate.id) 'Duplicate answer created a new attempt'
$p2First = Wait-P2 $p2First.id
$p2Reference = Get-P2 "/practice/attempts/$($p2First.id)/reference"
Check-P2 ($p2Reference.criteria.Count -eq 3) 'Reference criteria missing'
$p2Answer = ($p2Reference.criteria | ForEach-Object { $_.acceptedExpressions[0] }) -join ' '
$p2Second = Post-P2 "/practice/sessions/$($p2Session.id)/attempts" @{
    answer=$p2Answer; inputMode='TEXT'; parentAttemptId=$p2First.id; clientRequestId=[guid]::NewGuid().ToString()
}
$p2Second = Wait-P2 $p2Second.id
Check-P2 ($p2Second.comparison.comparable) 'Retry comparison unavailable'
Check-P2 ($p2Second.referenceViewed) 'Assistance was not recorded'
Check-P2 (($p2Second.comparison.criteria | Where-Object { $_.change -eq 'NEWLY_COVERED' }).Count -gt 0) 'No newly covered criteria'
$p2FollowKey = [guid]::NewGuid().ToString()
$p2Followup = Post-P2 "/practice/attempts/$($p2First.id)/followups" @{ clientRequestId=$p2FollowKey }
$p2ReplayFollowup = Post-P2 "/practice/attempts/$($p2First.id)/followups" @{ clientRequestId=$p2FollowKey }
Check-P2 ($p2Followup.id -eq $p2ReplayFollowup.id) 'Duplicate follow-up created a new session'
Check-P2 ($p2Followup.kind -eq 'FOLLOWUP' -and $p2Followup.originSessionId -eq $p2Session.id) 'Follow-up origin missing'
$p2FollowAnswer = Post-P2 "/practice/sessions/$($p2Followup.id)/attempts" @{
    answer=$p2Answer; inputMode='TEXT'; parentAttemptId=$null; clientRequestId=[guid]::NewGuid().ToString()
}
$p2FollowAnswer = Wait-P2 $p2FollowAnswer.id
Check-P2 ($p2FollowAnswer.evaluation.result.criteria.Count -eq 1) 'Follow-up should evaluate the selected criterion'
$p2Reloaded = Get-P2 "/practice/sessions/$($p2Session.id)"
Check-P2 ($p2Reloaded.attempts.Count -eq 2) 'Original attempts changed'
Check-P2 ($p2Reloaded.attempts[0].answer -eq $p2FirstBody.answer) 'First answer was overwritten'
Check-P2 (-not $p2Reloaded.attempts[0].referenceViewed) 'First answer assistance flags were changed retroactively'
Check-P2Status { Post-P2 "/practice/sessions/$($p2Session.id)/attempts" @{
    answer='不同的回答'; inputMode='TEXT'; parentAttemptId=$null; clientRequestId=$p2FirstBody.clientRequestId
} } 409
Check-P2Status { Post-P2 "/practice/sessions/$($p2Session.id)/attempts" @{
    answer=''; inputMode='TEXT'; parentAttemptId=$p2Second.id; clientRequestId=[guid]::NewGuid().ToString()
} } 400
$p2History = Get-P2 '/practice/sessions?size=50'
Check-P2 (($p2History.items | Where-Object { $_.id -eq $p2Session.id }).Count -eq 1) 'History session missing'
$p2Complete = Post-P2 "/practice/sessions/$($p2Session.id)/complete" @{}
Check-P2 ($p2Complete.status -eq 'COMPLETED') 'Complete transition failed'
$p2Report = [ordered]@{
    verifiedAt=[datetimeoffset]::Now.ToString('o'); result='PASS'; sessionId=$p2Session.id; followupSessionId=$p2Followup.id;
    firstAttemptId=$p2First.id; secondAttemptId=$p2Second.id; duplicateSubmit='one-attempt-one-evaluation';
    firstAnswerPreserved=$true; referenceAssistanceRecorded=$true; followupOriginPreserved=$true;
    referenceState=$p2Second.evaluation.referenceState; model=$p2Second.evaluation.modelName;
    comparison=$p2Second.comparison.criteria; invalidRequests=3; status=$p2Complete.status
}
$p2Report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $ReportPath -Encoding UTF8
'P2 actual HTTP training lifecycle: PASS'

