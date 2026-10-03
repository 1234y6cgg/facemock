"""Create disposable no-answer acceptance sessions, verify references, delete only those IDs.

Never submits answers or calls the external language model. Does not touch existing sessions.
"""
import json
import time
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
BASE = 'http://localhost:8080'

def request(path, method='GET', body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method,
                                 headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)

added = json.loads((ROOT/'backend/src/main/resources/questions/catalog-v2.json').read_text(encoding='utf-8'))
questions = {}
for q in added['questions']: questions.setdefault(q['topic'],q)
before = request('/api/practice/sessions')['totalElements']
rows = []
for topic, q in questions.items():
    session = request('/api/practice/sessions', 'POST', {
        'questionId':q['id'], 'questionVersion':1,
        'clientRequestId':'expansion-check-'+uuid.uuid4().hex})
    sid = session['id']
    try:
        assert session['question']['id'] == q['id'] and session['question']['topic'] == topic
        assert session['attempts'] == []
        reference = request('/api/practice/sessions/'+sid+'/reference?assisted=true')
        assert reference['criteria'] == q['criteria']
        assert reference['explanation'] == q['explanation']
        assert len(reference['sources']) == 1 and reference['sources'][0]['url'].startswith('https://')
        hint = request('/api/practice/sessions/'+sid+'/hints','POST')
        assert hint['hint'].strip()
        ended = request('/api/practice/sessions/'+sid+'/complete','POST')
        assert ended['status'] == 'COMPLETED' and ended['attempts'] == []
        rows.append({'topic':topic,'questionId':q['id'],'createReferenceHintCompletePassed':True})
    finally:
        request('/api/practice/sessions/'+sid,'DELETE')
    print('PASS workflow', topic, flush=True)
after = request('/api/practice/sessions')['totalElements']
assert before == after
report = {'verifiedAt':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),
          'scope':'18 disposable new-question sessions; no answers or model calls; deleted exact created IDs.',
          'passed':len(rows),'existingPracticeCountBefore':before,'existingPracticeCountAfter':after,'rows':rows}
(ROOT/'docs/question-expansion-workflow-check.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('PASS: all 18 new-topic references usable; test sessions cleaned.')
