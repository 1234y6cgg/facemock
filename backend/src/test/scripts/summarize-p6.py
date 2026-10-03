"""Summarize real predictions, never fill missing predictions with expected labels."""
import argparse, json, math
from collections import Counter
from pathlib import Path

def ratio(numerator, denominator):
    return dict(numerator=numerator, denominator=denominator, rate=numerator/denominator if denominator else None)

def summarize(report):
    rows=report['cases']; expected_total=matches=wrong_total=wrong_accepted=wrong_partial=correct_total=correct_rejected=uncertain=predicted_total=0
    confusion=Counter(); differences=[]
    for row in rows:
        predicted={c['criterionId']:c['status'] for c in row.get('result',{}).get('criteria',[])} if row.get('validOutput') else {}
        for e in row['expected']:
            expected_total+=1; gold=e['status']; actual=predicted.get(e['criterionId'])
            if actual is not None:
                predicted_total+=1; matches+=actual==gold; uncertain+=actual=='UNCERTAIN'; confusion[gold+' -> '+actual]+=1
                if gold=='INCORRECT':wrong_total+=1;wrong_accepted+=actual=='COVERED';wrong_partial+=actual in ('COVERED','PARTIAL')
                if gold=='COVERED':correct_total+=1;correct_rejected+=actual!='COVERED'
            if actual!=gold:differences.append(dict(caseId=row['caseId'],criterionId=e['criterionId'],expected=gold,actual=actual or 'EVALUATION_FAILED'))
    called=[r for r in rows if r.get('modelCalled')]; durations=sorted(r['durationMs'] for r in called if r.get('validOutput'))
    known=[r['usage'] for r in called if 'total_tokens' in r.get('usage',{})]
    rag=[r for r in rows if 'retrievalValid' in r and r.get('sourceSnapshots')]
    return dict(cases=len(rows),modelCalls=len(called),validOutputs=sum(bool(r.get('validOutput')) for r in rows),
        agreementAllExpected=ratio(matches,expected_total),agreementValidPredictions=ratio(matches,predicted_total),
        factualErrorAcceptedAsCovered=ratio(wrong_accepted,wrong_total),factualErrorAcceptedAsCoveredOrPartial=ratio(wrong_partial,wrong_total),
        correctColloquialNotCovered=ratio(correct_rejected,correct_total),uncertain=ratio(uncertain,predicted_total),
        evaluationFailure=ratio(sum(not r.get('validOutput') for r in rows),len(rows)),
        retrievalBoundHit=ratio(sum(r.get('boundHitCount',0)>0 for r in rag),len(rag)),
        validatedCitationOutputs=ratio(sum(bool(r.get('validOutput')) for r in rows),len(rows)),
        latency=dict(successfulCalledSampleSize=len(durations),p50Ms=durations[math.ceil(.5*len(durations))-1] if len(durations)>=30 else None,
            p95Ms=durations[math.ceil(.95*len(durations))-1] if len(durations)>=30 else None),
        usage=dict(knownCalls=len(known),missingCalls=len(called)-len(known),totalTokens=sum(u['total_tokens'] for u in known) if known else None,
            promptTokens=sum(u.get('prompt_tokens',0) for u in known) if known else None,completionTokens=sum(u.get('completion_tokens',0) for u in known) if known else None),
        confusionMatrix=dict(sorted(confusion.items())),differences=differences,
        annotationStatus=report.get('annotationStatus','unspecified'),scope='Fixed development cases with rubric-derived expectations. Human review and user study are outside delivery scope; no generalization claim.')

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('report');parser.add_argument('--output',default='target/p6-metrics.json');args=parser.parse_args()
    result=summarize(json.loads(Path(args.report).read_text(encoding='utf-8-sig')));Path(args.output).write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in result.items() if k not in ('differences','confusionMatrix')},ensure_ascii=False,indent=2))
