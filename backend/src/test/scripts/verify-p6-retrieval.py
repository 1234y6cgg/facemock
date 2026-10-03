import argparse, json, time, urllib.request, urllib.parse, hashlib
from pathlib import Path
parser=argparse.ArgumentParser();parser.add_argument('--url',default='http://localhost:8080');parser.add_argument('--output',default='target/p6-retrieval-check.json');args=parser.parse_args()
root=Path(__file__).resolve().parents[3]
data=json.loads((root/'src/test/resources/questions/answer-cases-v2.json').read_text(encoding='utf-8'))
catalog=json.loads((root/'src/main/resources/questions/catalog-v1.json').read_text(encoding='utf-8'))
questions={q['id']:q for q in catalog['questions']};sources={s['id']:s['document'] for s in catalog['sources']}
rows=[]
for case in data['cases']:
    q=questions[case['questionId']];query=(q['prompt']+' '+case['answer'])[:500]
    row={'caseId':case['id'],'questionId':q['id']};start=time.monotonic()
    try:
        with urllib.request.urlopen(args.url.rstrip('/')+'/api/knowledge/search?'+urllib.parse.urlencode({'topK':5,'query':query}),timeout=30) as response:hits=json.load(response)
        bound=[h for h in hits if any(sources[s]['id']==h['documentId'] and h['content'] in sources[s]['content'] and h['sourceUrl']==sources[s]['sourceUrl'] for s in case['availableSourceIds'])]
        row.update(valid=True,retrievedCount=len(hits),boundHitCount=len(bound),documentIds=[h['documentId'] for h in bound])
    except Exception as e:row.update(valid=False,errorType=type(e).__name__)
    row['durationMs']=round((time.monotonic()-start)*1000);rows.append(row)
report={'verifiedAt':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),'scope':'Warm retrieval recheck, no model calls; does not overwrite the model run or its outage observations.',
    'datasetSha256':hashlib.sha256(json.dumps(data,ensure_ascii=False,sort_keys=True).encode()).hexdigest(),'cases':rows,'successful':sum(r['valid'] for r in rows),
    'sourceEligibleCases':sum(bool(c['availableSourceIds']) for c in data['cases']),'sourceBoundMatches':sum(r.get('boundHitCount',0)>0 for r in rows)}
Path(args.output).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k!='cases'},ensure_ascii=False,indent=2))
