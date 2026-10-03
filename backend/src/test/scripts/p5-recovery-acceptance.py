"""Recovery/delete checks in the explicitly named disposable P5 environment only."""
import json,time,uuid,urllib.request,urllib.error,subprocess,datetime
from pathlib import Path
BASE='http://127.0.0.1:18083'
DOCKER=r'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
def api(path,body=None,method=None):
    req=urllib.request.Request(BASE+'/api'+path,data=json.dumps(body).encode() if body is not None else None,headers={'Content-Type':'application/json'},method=method or ('POST' if body is not None else 'GET'))
    with urllib.request.urlopen(req,timeout=30) as r:return json.load(r) if r.status!=204 else None
def key():return str(uuid.uuid4())
def restart(mode):
    pid=int(Path('target/p5-acceptance.pid').read_text().strip());assert pid>0
    # Verify command line before stopping; never target the user deployment.
    command=f"$p=Get-CimInstance Win32_Process -Filter 'ProcessId={pid}'; if($p.CommandLine -notmatch 'server.port=18083'){{throw 'Unexpected process'}}; Stop-Process -Id {pid}"
    subprocess.run(['powershell','-NoProfile','-Command',command],check=True,capture_output=True)
    subprocess.run([DOCKER,'exec','facemock-p5-acceptance-redis','redis-cli','FLUSHALL'],check=True,capture_output=True)
    subprocess.run(['powershell','-NoProfile','-File','src/test/scripts/start-p5-acceptance.ps1','-ModelMode',mode],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    for _ in range(90):
        try:
            if api('/health')['status']=='UP':return
        except Exception:pass
        time.sleep(1)
    raise TimeoutError('application restart')
def wait(a,status):
    for _ in range(45):
        v=api('/practice/attempts/'+a['id'])
        if v['evaluation']['status']==status:return v
        if v['evaluation']['status'] in ('SUCCEEDED','FAILED'):raise AssertionError(v['evaluation']['status'])
        time.sleep(2)
    raise TimeoutError('evaluation')
fixture=json.loads(Path('target/p5-http-acceptance.json').read_text(encoding='utf-8'))
before=api('/progress/knowledge-points');restart('Live');after=api('/progress/knowledge-points')
assert before==after
restart('Unavailable')
s=api('/practice/sessions',dict(questionId='mysql.composite-index',questionVersion=1,clientRequestId=key()))
a=api('/practice/sessions/'+s['id']+'/attempts',dict(answer='联合索引从左侧列开始匹配，范围条件会限制后续列利用。',inputMode='TEXT',parentAttemptId=None,clientRequestId=key()))
failed=wait(a,'FAILED');v=api('/progress/knowledge-points');assert v['validAnswers']==before['validAnswers'];assert v['failedEvaluations']==1
restart('Live')
retried=wait(api('/practice/attempts/'+a['id']+'/retry-evaluation',dict(clientRequestId=key())),'SUCCEEDED')
assert retried['id']==a['id'];assert retried['answer']==failed['answer'];assert len(api('/practice/sessions/'+s['id'])['attempts'])==1
api('/practice/sessions/'+s['id'],method='DELETE')
api('/practice/sessions/'+fixture['baselineSession'],method='DELETE')
point=next(p for p in api('/progress/knowledge-points')['points'] if p['id']==fixture['pointId']);assert point['independentPasses']==1;assert point['state']=='TO_CONSOLIDATE'
for sid in (fixture['firstReview'],fixture['secondReview']):api('/practice/sessions/'+sid,method='DELETE')
empty=api('/progress/knowledge-points');assert empty['validAnswers']==0;assert empty['independentPasses']==0;assert all(p['dueDate'] is None for p in empty['points'])
report=dict(verifiedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),disposableDatabase='p5check',restartAfterRedisFlushIdentical=True,failedEvaluationExcluded=True,retrySameImmutableAnswer=True,deleteBaselineReplayed=True,deleteAllRemovedProgress=True,actualModelRetry=True)
Path('target/p5-recovery-acceptance.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8');print('P5 restart, failure/retry and deletion checks passed.')
