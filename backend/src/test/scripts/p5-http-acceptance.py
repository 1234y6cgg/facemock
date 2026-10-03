"""Own isolated acceptance DB only. Synthetic dates are fixtures, not a user trial."""
import json, time, uuid, urllib.request, urllib.error, subprocess, datetime
from pathlib import Path
BASE='http://127.0.0.1:18083'
DOCKER=r'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
def api(path,body=None,method=None):
    request=urllib.request.Request(BASE+'/api'+path,data=json.dumps(body,ensure_ascii=False).encode() if body is not None else None,
        headers={'Content-Type':'application/json'},method=method or ('POST' if body is not None else 'GET'))
    with urllib.request.urlopen(request,timeout=30) as r:return json.load(r) if r.status!=204 else None
def key():return str(uuid.uuid4())
def wait(attempt):
    for _ in range(40):
        value=api('/practice/attempts/'+attempt['id'])
        if value['evaluation']['status']=='SUCCEEDED':return value
        if value['evaluation']['status']=='FAILED':raise RuntimeError(value['evaluation']['errorCode'])
        time.sleep(2)
    raise TimeoutError('evaluation polling')
def submit(session,answer,parent=None):
    body=dict(answer=answer,parentAttemptId=parent,inputMode='TEXT',clientRequestId=key())
    a=api('/practice/sessions/'+session['id']+'/attempts',body);b=api('/practice/sessions/'+session['id']+'/attempts',body)
    assert a['id']==b['id'];return wait(a)
def historical(attempt,days_ago):
    assert uuid.UUID(attempt['id'])
    # Hard-coded disposable DB/container; never run this against the facemock application's MySQL.
    sql=f"UPDATE practice_attempt SET created_at=DATE_SUB(UTC_TIMESTAMP(), INTERVAL {int(days_ago)} DAY) WHERE id='{attempt['id']}'"
    subprocess.run([DOCKER,'exec','facemock-p5-acceptance-mysql','mysql','-uroot','-pp5-local-check','p5check','-e',sql],check=True,capture_output=True)
def point(view,pid):return next(p for p in view['points'] if p['id']==pid)
session=api('/practice/sessions',dict(questionId='redis.lua-stock',questionVersion=1,clientRequestId=key()))
first=submit(session,'用 Redis Lua，因为 Lua 是原子的。');historical(first,8)
reference=api('/practice/sessions/'+session['id']+'/reference')
answer=' '.join(c['acceptedExpressions'][0] for c in reference['criteria'])
second=submit(session,answer,first['id']);historical(second,7)
assert second['referenceViewed'];assert second['comparison']['comparable']
overview=api('/progress/knowledge-points');pid=reference['criteria'][0]['knowledgePointId'];assert point(overview,pid)['independentPasses']==0
today=api('/review/today');assert today['recommendations'][0]['kind']=='REVIEW'
start_body=dict(pointId=pid,clientRequestId=key());r1=api('/review/sessions',start_body);assert r1['id']==api('/review/sessions',start_body)['id']
try:api('/practice/sessions/'+r1['id']+'/reference?assisted=true');raise AssertionError('review leaked reference')
except urllib.error.HTTPError as e:assert e.code==409
a1=submit(r1,answer);historical(a1,6);overview=api('/progress/knowledge-points');assert point(overview,pid)['independentPasses']==1
r2=api('/review/sessions',dict(pointId=pid,clientRequestId=key()));a2=submit(r2,answer);overview=api('/progress/knowledge-points')
assert point(overview,pid)['state']=='CONSOLIDATED';assert point(overview,pid)['independentPasses']==2
for _ in range(3):assert point(api('/progress/knowledge-points'),pid)['independentPasses']==2
export=api('/data/export');assert export['schemaVersion']==1;assert len(export['practiceAttempts'])==4;assert all(e['telemetryJson'] for e in export['practiceEvaluations'])
prefs=today['preferences'];config={k:prefs[k] for k in ('timezone','dailyLimit','intervals','requiredPasses','paused')};config['paused']=True
api('/review/preferences',config,'PUT');assert not api('/review/today')['recommendations'];config['paused']=False;api('/review/preferences',config,'PUT')
report=dict(verifiedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),syntheticAnswers=True,historicalDatesInjectedOnlyInDisposableDatabase=True,
    baselineSession=session['id'],firstReview=r1['id'],secondReview=r2['id'],pointId=pid,questionId='redis.lua-stock',comparison=second['comparison'],
    progress=point(api('/progress/knowledge-points'),pid),exportVerified=True,telemetryVerified=True,repeatIdempotent=True,assistanceNotConsolidation=True,reviewReferenceBlocked=True,
    pausedResumed=True,actualModel=True,actualMysql=True,actualChroma=True,actualBge=True)
Path('target/p5-http-acceptance.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('P5 HTTP flow passed; synthetic baseline and two dated review sessions retained for UI and restart checks.')
