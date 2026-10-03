"""Compile reviewed authoring rows into the additive, versioned question catalog.

No network access and no scraping. Rows are original training prompts/rubrics;
the linked official material is used to verify technical claims.
"""
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
AUTHORING = ROOT / 'docs/question-expansion-authoring.txt'
OUTPUT = ROOT / 'backend/src/main/resources/questions/catalog-v2.json'

JAVA = 'https://docs.oracle.com/en/java/javase/17/docs/api/java.base/'
JLS = 'https://docs.oracle.com/javase/specs/jls/se17/html/'
JVMS = 'https://docs.oracle.com/javase/specs/jvms/se17/html/'
MYSQL = 'https://dev.mysql.com/doc/refman/8.0/en/'
SPRING = 'https://docs.spring.io/spring-framework/reference/'
OWASP = 'https://cheatsheetseries.owasp.org/cheatsheets/'
REFS = {
    'types': JLS+'jls-4.html', 'classes': JLS+'jls-8.html', 'exceptions': JLS+'jls-11.html',
    'memory-model': JLS+'jls-17.html', 'object': JAVA+'java/lang/Object.html',
    'string': JAVA+'java/lang/String.html', 'decimal': JAVA+'java/math/BigDecimal.html',
    'proxy': JAVA+'java/lang/reflect/Proxy.html', 'arraylist': JAVA+'java/util/ArrayList.html',
    'linkedlist': JAVA+'java/util/LinkedList.html', 'hashmap': JAVA+'java/util/HashMap.html',
    'treemap': JAVA+'java/util/TreeMap.html', 'priorityqueue': JAVA+'java/util/PriorityQueue.html',
    'concurrent-map': JAVA+'java/util/concurrent/ConcurrentHashMap.html',
    'copyonwrite': JAVA+'java/util/concurrent/CopyOnWriteArrayList.html',
    'concurrent': JAVA+'java/util/concurrent/package-summary.html',
    'selector': JAVA+'java/nio/channels/Selector.html', 'buffer': JAVA+'java/nio/ByteBuffer.html',
    'stream': JAVA+'java/io/InputStream.html', 'files': JAVA+'java/nio/file/Files.html',
    'file-channel': JAVA+'java/nio/channels/FileChannel.html',
    'references': JAVA+'java/lang/ref/package-summary.html',
    'jit': 'https://docs.oracle.com/en/java/javase/17/vm/java-hotspot-virtual-machine-performance-enhancements.html',
    'lru': JAVA+'java/util/LinkedHashMap.html',
    'resources': JLS+'jls-14.html', 'threadlocal': 'https://github.com/openjdk/jdk17u/blob/master/src/java.base/share/classes/java/lang/ThreadLocal.java',
    'atomic': JAVA+'java/util/concurrent/atomic/package-summary.html',
    'lock': JAVA+'java/util/concurrent/locks/ReentrantLock.html',
    'aqs': JAVA+'java/util/concurrent/locks/AbstractQueuedSynchronizer.html',
    'future': JAVA+'java/util/concurrent/CompletableFuture.html',
    'jvm-structure': JVMS+'jvms-2.html', 'jvm-loading': JVMS+'jvms-5.html',
    'gc': 'https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-g1-garbage-collector1.html',
    'jcmd': 'https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html',
    'mysql-index': MYSQL+'innodb-index-types.html', 'mysql-lock': MYSQL+'innodb-locking.html',
    'mysql-redo': MYSQL+'innodb-redo-log.html', 'mysql-binlog': MYSQL+'binary-log.html',
    'mysql-undo': MYSQL+'innodb-undo-logs.html', 'mysql-replication': MYSQL+'replication.html',
    'mysql-deadlock': MYSQL+'innodb-deadlocks.html', 'mysql-optimizer': MYSQL+'optimization.html',
    'mysql-buffer': MYSQL+'innodb-buffer-pool.html',
    'redis-types': 'https://redis.io/docs/latest/develop/data-types/',
    'redis-client': 'https://redis.io/docs/latest/develop/reference/client-side-caching/',
    'redis-replication': 'https://redis.io/docs/latest/operate/oss_and_stack/management/replication/',
    'redis-sentinel': 'https://redis.io/docs/latest/operate/oss_and_stack/management/sentinel/',
    'redis-cluster': 'https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/',
    'redis-latency': 'https://redis.io/docs/latest/operate/oss_and_stack/management/optimization/latency/',
    'spring-ioc': SPRING+'core/beans/introduction.html',
    'spring-scopes': SPRING+'core/beans/factory-scopes.html',
    'spring-lifecycle': SPRING+'core/beans/factory-nature.html',
    'spring-proxy': SPRING+'core/aop/proxying.html',
    'spring-cycle': SPRING+'core/beans/dependencies/factory-collaborators.html',
    'spring-mvc': SPRING+'web/webmvc/mvc-servlet.html',
    'spring-validation': SPRING+'core/validation/beanvalidation.html',
    'spring-events': SPRING+'data-access/transaction/event.html',
    'boot': 'https://docs.spring.io/spring-boot/reference/using/auto-configuration.html',
    'boot-config': 'https://docs.spring.io/spring-boot/reference/features/external-config.html',
    'mybatis': 'https://mybatis.org/mybatis-3/sqlmap-xml.html',
    'mybatis-dynamic': 'https://mybatis.org/mybatis-3/dynamic-sql.html',
    'mybatis-java': 'https://mybatis.org/mybatis-3/java-api.html',
    'mybatis-spring': 'https://mybatis.org/spring/transactions.html',
    'tcp': 'https://www.rfc-editor.org/rfc/rfc9293.html',
    'http': 'https://www.rfc-editor.org/rfc/rfc9110.html',
    'http-cache': 'https://www.rfc-editor.org/rfc/rfc9111',
    'http2': 'https://www.rfc-editor.org/rfc/rfc9113',
    'quic': 'https://www.rfc-editor.org/rfc/rfc9000',
    'tls': 'https://www.rfc-editor.org/rfc/rfc8446', 'dns': 'https://www.rfc-editor.org/rfc/rfc1034',
    'websocket': 'https://www.rfc-editor.org/rfc/rfc6455',
    'os': 'https://pages.cs.wisc.edu/~remzi/OSTEP/',
    'algorithms': 'https://algs4.cs.princeton.edu/cheatsheet/',
    'kafka': 'https://kafka.apache.org/41/design/design/',
    'mq-ack': 'https://www.rabbitmq.com/docs/confirms', 'mq-dlx': 'https://www.rabbitmq.com/docs/dlx',
    'mq-routing': 'https://www.rabbitmq.com/tutorials/amqp-concepts',
    'mq-transaction': 'https://rocketmq.apache.org/docs/featureBehavior/04transactionmessage/',
    'patterns': 'https://learn.microsoft.com/en-us/azure/architecture/patterns/',
    'raft': 'https://raft.github.io/raft.pdf',
    'dynamo': 'https://www.allthingsdistributed.com/files/amazon-dynamo-sosp2007.pdf',
    'outbox': 'https://microservices.io/patterns/data/transactional-outbox.html',
    'saga': 'https://microservices.io/patterns/data/saga.html',
    'overload': 'https://sre.google/sre-book/handling-overload/',
    'monitoring': 'https://sre.google/sre-book/monitoring-distributed-systems/',
    'sql-injection': OWASP+'SQL_Injection_Prevention_Cheat_Sheet.html',
    'xss': OWASP+'Cross_Site_Scripting_Prevention_Cheat_Sheet.html',
    'csrf': OWASP+'Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html',
    'password': OWASP+'Password_Storage_Cheat_Sheet.html',
    'authorization': OWASP+'Authorization_Cheat_Sheet.html',
    'ssrf': OWASP+'Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html',
    'jwt': 'https://www.rfc-editor.org/rfc/rfc8725.html',
    'secrets': OWASP+'Secrets_Management_Cheat_Sheet.html',
    'container': 'https://docs.docker.com/get-started/docker-concepts/the-basics/what-is-a-container/',
    'volume': 'https://docs.docker.com/engine/storage/volumes/',
    'git': 'https://git-scm.com/docs/git-rebase',
    'git-reset': 'https://git-scm.com/docs/git-reset',
    'health': 'https://kubernetes.io/docs/concepts/configuration/liveness-readiness-startup-probes/',
    'linux': 'https://www.kernel.org/doc/html/latest/admin-guide/',
}

def build():
    catalog = dict(catalogVersion=2, knowledgePoints=[], sources=[], questions=[])
    topic = None
    for number, line in enumerate(AUTHORING.read_text(encoding='utf-8').splitlines(), 1):
        if not line.strip(): continue
        if line.startswith('# '): topic = line[2:].strip(); continue
        fields = line.split('|')
        if len(fields) != 9 or not topic: raise ValueError(f'Invalid row {number}')
        qid, difficulty, title, prompt, ref, *rest = fields
        criteria_rows, followup = rest[:3], rest[3]
        criteria = []
        sid = qid+'.official'
        for i, row in enumerate(criteria_rows, 1):
            label, expected, accepted, mistake = row.split('~')
            pid = f'{qid}.point{i}'
            catalog['knowledgePoints'].append(dict(id=pid, title=title+'：'+label, topic=topic, description=expected))
            criteria.append(dict(id=f'point{i}',knowledgePointId=pid,expected=expected,
                                 acceptedExpressions=[accepted],commonMistakes=[mistake],sourceIds=[sid]))
        content = '\n'.join(c['expected'] for c in criteria)
        catalog['sources'].append(dict(id=sid, version=1, verifiedOn='2026-10-03', document=dict(
            id='p1.'+qid+'.v1',title=title,content=content,source='项目原创训练要点；按官方文档/原始论文核对，非站点原文转载',
            sourceUrl=REFS[ref],tags=[topic,title[:40]])))
        catalog['questions'].append(dict(id=qid,version=1,rubricVersion=1,topic=topic,difficulty=int(difficulty),
            title=title,prompt=prompt,suggestedSeconds=90 if int(difficulty)<3 else 120 if int(difficulty)<5 else 180,
            active=True,criteria=criteria,explanation=content+'\n表达时先讲机制，再落到题目场景，最后说明限制；正确的其他实现或等价表达也可成立。',followups=[followup]))
    ids=[q['id'] for q in catalog['questions']]
    assert len(ids)==len(set(ids)), 'Duplicate question IDs'
    base=json.loads((OUTPUT.parent/'catalog-v1.json').read_text(encoding='utf-8'))
    assert not set(ids)&{q['id'] for q in base['questions']}, 'Rewrites existing questions'
    OUTPUT.write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('New questions:',len(ids),'All:',len(ids)+len(base['questions']))
    print(dict(Counter(q['topic'] for q in base['questions']+catalog['questions'])))

if __name__ == '__main__': build()
