"""Deterministic fixture construction, never use model predictions as gold labels.

The 24 previous cases are preserved; new cases reuse source-verified rubric
expressions or explicit common mistakes. Independent human review is outside the current delivery scope.
"""
import json
from pathlib import Path

root = Path(__file__).resolve().parents[3]
catalog = json.loads((root / 'src/main/resources/questions/catalog-v1.json').read_text(encoding='utf-8'))
previous = json.loads((root / 'src/test/resources/questions/answer-cases-v1.json').read_text(encoding='utf-8'))
cases = previous['cases'].copy()
for q in catalog['questions']:
    clauses = ['在我的理解里，' + c['acceptedExpressions'][0] for c in q['criteria']]
    cases.append(dict(id='p6-oral-' + q['id'], questionId=q['id'], questionVersion=q['version'],
        category='COLLOQUIAL_CORRECT', answer=' '.join(clauses),
        availableSourceIds=list(dict.fromkeys(s for c in q['criteria'] for s in c['sourceIds'])),
        expected=[dict(criterionId=c['id'], status='COVERED', candidateQuotes=[clause],
            reason='语义来自经核验的 acceptedExpressions；自然口语前缀不改变事实。', sourceIds=c['sourceIds'])
            for c, clause in zip(q['criteria'], clauses)],
        reviewNote='规则资料支持的预期标签；不以模型输出作为标签；独立人工复核不在当前交付范围内。'))
for q in catalog['questions'][::2]:
    first=q['criteria'][0]
    answer='我的结论是：' + first['commonMistakes'][0]
    cases.append(dict(id='p6-wrong-' + q['id'], questionId=q['id'], questionVersion=q['version'],
        category='FACTUAL_ERROR', answer=answer,
        availableSourceIds=list(dict.fromkeys(s for c in q['criteria'] for s in c['sourceIds'])),
        expected=[dict(criterionId=c['id'], status='INCORRECT' if i==0 else 'MISSING',
            candidateQuotes=[answer] if i==0 else [],reason='明确肯定已核验的易错说法。' if i==0 else '没有回答该要点。',sourceIds=c['sourceIds'])
            for i,c in enumerate(q['criteria'])],
        reviewNote='显式易错说法与遗漏组合；要点交叉覆盖可能造成预期与实际差异；独立人工复核不在当前交付范围内。'))
assert len(cases)==60
out=dict(datasetVersion=2,provenance='24 existing P1/P2 cases + 24 oral variants + 12 explicit misconception cases',
    annotationStatus='Rubric-derived expectations; independent human review outside delivery scope',
    scope='Fixed development benchmark, not an unseen accuracy estimate',cases=cases)
(root/'src/test/resources/questions/answer-cases-v2.json').write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'Wrote {len(cases)} fixed cases; independent human review is outside delivery scope.')
