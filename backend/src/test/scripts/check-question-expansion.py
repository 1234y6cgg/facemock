"""Read-only deployed catalog/RAG checks. Saves counts and public metadata only."""
import argparse
import json
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
parser = argparse.ArgumentParser()
parser.add_argument('--url', default='http://localhost:8080')
parser.add_argument('--output', default='docs/question-expansion-deployment-check.json')
args = parser.parse_args()

def get(path, **params):
    url = args.url.rstrip('/') + path
    if params: url += '?' + urllib.parse.urlencode(params)
    with urllib.request.urlopen(url, timeout=45) as response:
        return json.load(response)

base = json.loads((ROOT/'backend/src/main/resources/questions/catalog-v1.json').read_text(encoding='utf-8'))
added = json.loads((ROOT/'backend/src/main/resources/questions/catalog-v2.json').read_text(encoding='utf-8'))
all_questions = base['questions'] + added['questions']
expected = Counter(q['topic'] for q in all_questions)
summary = get('/api/questions/topics')
assert summary['totalQuestions'] == len(all_questions) == 180
assert {t['topic']: t['count'] for t in summary['topics']} == dict(expected)
topic_rows = []
for topic, count in expected.items():
    page = get('/api/questions', topic=topic, size=50)
    assert page['totalElements'] == count
    assert all(q['topic'] == topic for q in page['items'])
    topic_rows.append({'topic': topic, 'count': count, 'filterPassed': True})
all_ids = []
for page in range(15):
    listing = get('/api/questions', page=page, size=12)
    all_ids.extend(q['id'] for q in listing['items'])
    assert all('criteria' not in q and 'explanation' not in q for q in listing['items'])
assert len(set(all_ids)) == 180
assert set(all_ids) == {q['id'] for q in all_questions}
public_fields = ('id', 'version', 'topic', 'difficulty', 'title', 'prompt', 'suggestedSeconds')
for q in base['questions']:
    assert get('/api/questions/' + q['id'], version=1) == {k:q[k] for k in public_fields}
searched = get('/api/questions', topic='JAVA_COLLECTIONS', q='hAsHmAp', size=12)
assert searched['totalElements'] > 0
assert all('hashmap' in q['title'].lower() for q in searched['items'])
assert get('/api/questions', q='%')['totalElements'] == 0
assert get('/api/questions', q='_')['totalElements'] == 1
try:
    get('/api/questions', q='a'*81)
    raise AssertionError('Long query accepted')
except urllib.error.HTTPError as error:
    assert error.code == 400
knowledge = get('/api/knowledge/status')
assert knowledge['available'] and knowledge['totalChunks'] == 190, knowledge
retrieval = []
for topic in expected:
    q = next(q for q in added['questions'] if q['topic'] == topic)
    source_id = q['criteria'][0]['sourceIds'][0]
    source = next(s['document'] for s in added['sources'] if s['id'] == source_id)
    # Match the production resolver's title + prompt prefix, before answer text.
    query = q['title'] + ' ' + q['prompt']
    hits = get('/api/knowledge/search', query=query, topK=5)
    bound = [(i,h) for i,h in enumerate(hits,1) if h['documentId']==source['id']]
    rank = bound[0][0] if bound else None
    if bound:
        h = bound[0][1]
        assert h['content'] in source['content'] and h['sourceUrl'] == source['sourceUrl']
    retrieval.append({'topic':topic, 'questionId':q['id'], 'query':query,
                      'expectedDocumentId':source['id'], 'rank':rank,
                      'returnedDocumentIds':[h['documentId'] for h in hits]})
    print('RAG', topic, 'bound rank', rank, flush=True)
# Read private export only in memory. Publish counts, never rows or snapshots.
private_data = get('/api/data/export')
counts = {k:len(private_data[k]) for k in ('resumes','interviews','projects','practiceSessions','projectSessions','recordings')}
before = json.loads((ROOT/'backend/target/question-expansion-before.json').read_text(encoding='utf-8'))
assert counts == before['counts']
progress = get('/api/progress/knowledge-points')
assert len(progress['points']) == 540
model = get('/api/settings/model')
assert model == before['model']
report = {'verifiedAt':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),
          'scope':'Existing facemock deployment; no model calls; private export reduced to counts.',
          'questions':180, 'topics':topic_rows, 'knowledgePoints':540, 'knowledge':knowledge,
          'legacyPublicQuestionsUnchanged':24,'paginationUniqueQuestions':len(set(all_ids)),
          'titleSearchLiteralAndCombinedPassed':True, 'longQueryRejected':True,
          'personalDataCountsBefore':before['counts'],'personalDataCountsAfter':counts,
          'modelSettingsUnchanged':True,'retrieval':retrieval,
          'boundRetrievalTop5':sum(r['rank'] is not None for r in retrieval),
          'retrievalChecks':len(retrieval),
          'newQuestionModelCalibration':'Pending; historical P6 metrics do not cover these new questions.'}
(ROOT/args.output).write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert all(r['rank'] is not None for r in retrieval), 'Some bound sources were not in top 5; inspect report'
print('PASS: 180 questions / 18 topics / 540 points / 190 chunks; preserved personal data and model settings.')
