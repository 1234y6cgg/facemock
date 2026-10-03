"""Read-only checks. Persist only counts/status, never private exported records."""
import json,urllib.request,urllib.parse,pathlib,datetime,re
BASE='http://localhost:8080'
def get(path):
    with urllib.request.urlopen(BASE+'/api/'+path,timeout=30) as r:return json.load(r)
pre=json.loads(pathlib.Path('backend/target/p5-pre-deploy-counts.json').read_text(encoding='utf-8'))
counts={k:len(get(v)) for k,v in dict(resumes='projects/resumes',interviews='interviews',projects='projects').items()}
assert counts==pre,(counts,pre)
progress=get('progress/knowledge-points');today=get('review/today')
expected_points=sum(len(json.loads(pathlib.Path('backend/src/main/resources/questions/'+name).read_text(encoding='utf-8'))['knowledgePoints']) for name in ('catalog-v1.json','catalog-v2.json'))
assert len(progress['points'])==expected_points
assert len(today['recommendations'])<=today['preferences']['dailyLimit']
export=get('data/export');assert export['schemaVersion']==1
knowledge=get('knowledge/status');hits=get('knowledge/search?'+urllib.parse.urlencode({'query':'Redis Lua 原子性 库存扣减 超卖','topK':3}))
assert knowledge['available'];assert len(hits)>0
with urllib.request.urlopen(BASE) as r:html=r.read().decode()
js=re.findall(r'/assets/index-[^" ]+\.js',html);assert js
for route in ('/mock','/review','/progress','/projects','/practice/questions'):
    with urllib.request.urlopen(BASE+route) as r:assert r.status==200
report=dict(verifiedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),health=get('health'),before=pre,after=counts,originalCountsPreserved=True,
    knowledgePoints=len(progress['points']),recommendations=len(today['recommendations']),knowledge=knowledge,ragHits=len(hits),frontend=js,
    exportSchema=export['schemaVersion'],exportCounts={k:len(export[k]) for k in ('resumes','interviews','practiceSessions','projects','projectSessions','recordings')})
pathlib.Path('docs/p5-p6-deployment-check.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print({k:v for k,v in report.items() if k!='knowledge'})
