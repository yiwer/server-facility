"""Extract release inventories without Maven, network, installs or source mutation.

The all-mode revision log must name --source; old evidence cannot be relabeled.
Outputs are declaration/dependency evidence, not a claim that release gates passed.
"""
from pathlib import Path
import argparse, hashlib, json, re, struct, subprocess, zipfile
import xml.etree.ElementTree as ET
from collections import Counter

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def class_info(data):
    p = 8
    def take(n):
        nonlocal p
        b = data[p:p+n]; p += n; return b
    def u1(): return take(1)[0]
    def u2(): return struct.unpack('>H', take(2))[0]
    def u4(): return struct.unpack('>I', take(4))[0]
    count = u2(); cp = [None] * count; i = 1
    while i < count:
        tag = u1()
        if tag == 1: cp[i] = take(u2()).decode('utf-8', errors='replace')
        elif tag in (7, 8, 16, 19, 20): cp[i] = u2()
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18): take(4)
        elif tag in (5, 6): take(8); i += 1
        elif tag == 15: take(3)
        else: raise ValueError(('constant tag', tag))
        i += 1
    def name(idx): return cp[cp[idx]].replace('/', '.') if idx else None
    flags = u2(); this = u2(); superclass = u2()
    interfaces = [name(u2()) for _ in range(u2())]
    for member_kind in range(2):
        for _ in range(u2()):
            take(6)
            for _ in range(u2()): u2(); take(u4())
    inner = None; outer = None
    for _ in range(u2()):
        attr = cp[u2()]; length = u4(); end = p + length
        if attr == 'InnerClasses':
            for _ in range(u2()):
                inner_idx, outer_idx, inner_name, inner_flags = u2(), u2(), u2(), u2()
                if inner_idx == this: inner = inner_flags; outer = name(outer_idx)
        p = end
    return {'name': name(this), 'classFlags': flags, 'innerFlags': inner,
            'outer': outer, 'superclass': name(superclass), 'interfaces': interfaces}

def inspect(path, javap):
    with zipfile.ZipFile(path) as z:
        infos = {x['name']: x for entry in sorted(z.namelist())
                 if entry.startswith('cn/code91/facility/') and entry.endswith('.class') and not entry.endswith('/package-info.class')
                 for x in [class_info(z.read(entry))]}
    def accessible(name, seen=None):
        item = infos[name]; flags = item['innerFlags'] if item['innerFlags'] is not None else item['classFlags']
        if not flags & 5: return False
        outer = item['outer']
        if outer: return outer in infos and accessible(outer)
        # A local/anonymous class has no outer index in InnerClasses; no exported API is inferred.
        if '$' in name: return False
        return True
    names = sorted(infos)
    chunks = []
    for offset in range(0, len(names), 40):
        r = subprocess.run([str(javap), '-protected', '-s', '-classpath', str(path), *names[offset:offset+40]],
                           capture_output=True, text=True, encoding='utf-8', errors='strict', check=True)
        if r.stderr.strip(): raise RuntimeError(r.stderr)
        chunks.append(r.stdout)
    raw = ''.join(chunks)
    parsed = {}; current = None; member = None
    for line in raw.splitlines():
        if line.endswith('{') and not line.startswith(' '):
            match = re.search(r'\b(?:class|interface|enum)\s+([\w.$]+)', line)
            if not match: raise ValueError(line)
            current = match.group(1); parsed[current] = {'declaration': line, 'members': {}}
        elif current and line.startswith('  ') and not line.startswith('    ') and line.rstrip().endswith(';'):
            member = line.strip()
        elif current and member and line.strip().startswith('descriptor:'):
            descriptor = line.split(':', 1)[1].strip()
            prefix = member.split('(', 1)[0] if '(' in member else member[:-1]
            name = prefix.rsplit(' ', 1)[-1]
            if name == current: name = '<init>'
            key = name + ' ' + descriptor
            if key in parsed[current]['members']: raise ValueError(('duplicate descriptor', current, key))
            parsed[current]['members'][key] = member; member = None
        elif line == '}': current = None; member = None
    if set(parsed) != set(infos):
        raise ValueError(('javap discovery differs from classfile inventory', set(infos)-set(parsed), set(parsed)-set(infos)))
    visible = {name: parsed[name] | {'classInfo': infos[name]} for name in names if accessible(name)}
    return {'artifact': str(path), 'sha256': sha(path), 'allClasses': len(infos),
            'accessibleClasses': len(visible), 'packages': dict(sorted(Counter(n.split('.')[3] for n in visible).items())),
            'javapMethod': 'javap -protected -s, batches of 40', 'javapSha256': hashlib.sha256(raw.encode('utf-8')).hexdigest(), 'classes': visible,
            'excludedClasses': {n: infos[n] for n in names if n not in visible}}

def compare(a, b):
    old, new = a['classes'], b['classes']; changes = []
    for name in sorted(old.keys() & new.keys()):
        am, bm = old[name]['members'], new[name]['members']
        removed = [{'key': k, 'declaration': am[k]} for k in sorted(am.keys()-bm.keys())]
        added = [{'key': k, 'declaration': bm[k]} for k in sorted(bm.keys()-am.keys())]
        same_descriptor = [{'key': k, 'before': am[k], 'after': bm[k]}
                           for k in sorted(am.keys() & bm.keys()) if am[k] != bm[k]]
        declaration = None if old[name]['declaration'] == new[name]['declaration'] else {
            'before': old[name]['declaration'], 'after': new[name]['declaration']}
        if removed or added or same_descriptor or declaration:
            changes.append({'class': name, 'removedDeclaredMembers': removed,
                            'addedDeclaredMembers': added, 'sameDescriptorChangedDeclaration': same_descriptor,
                            'classDeclarationChange': declaration})
    return {'removedClasses': sorted(old.keys()-new.keys()), 'addedClasses': sorted(new.keys()-old.keys()),
            'commonClassChanges': changes}


NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
REASONS = {
    'lombok': 'Compile-time generated boilerplate; optional runtime dependency, explicit annotation processor.',
    'jakarta.annotation-api': 'Public lifecycle/nullability annotation types.',
    'slf4j-api': 'Standard logging facade; host chooses its backend.',
    'spring-context': 'Application context, events, standard cache/executor boundaries.',
    'spring-beans': 'Bean contracts and explicit application configuration.',
    'spring-core': 'Spring foundational resource/type and bounded utility integration.',
    'spring-boot': 'Boot properties and application integration contracts.',
    'spring-boot-autoconfigure': 'Conditional auto-configuration and host override semantics.',
    'spring-boot-jackson': 'Boot4 technical Jackson module; auto-configuration no longer belongs to core.',
    'jakarta.validation-api': 'Declared validation annotations and HTTP validation translation.',
    'jackson-core': 'Jackson3 streaming/parser contracts; intentional public package migration.',
    'jackson-annotations': 'Jackson annotations retain the com.fasterxml package in Jackson3.',
    'jackson-databind': 'Jackson3 application-owned mapping; JDK8/time/parameter support built in.',
    'tika-core': 'Optional bounded MIME detection; explicit non-BOM pin, required capability fails absent.',
    'jakarta.servlet-api': 'Optional real Servlet contract; container selected by consuming application.',
    'spring-web': 'Optional standard HTTP and MVC-adjacent contracts.',
    'spring-webmvc': 'Optional MVC interception/advice and bounded HTTP adapters.',
    'jsoup': 'Optional mature HTML parser/sanitizer; explicit non-BOM version.',
    'caffeine': 'Optional finite local cache backend; pair with spring-context-support.',
    'spring-context-support': 'Optional CaffeineCacheManager integration; absent pair cannot silently fall back.',
    'poi': 'Optional XLS/core format engine; exact version aligned with OOXML.',
    'poi-ooxml': 'Optional XLSX/SXSSF engine and format graph; paired pin with POI core.',
    'commons-compress': 'Optional ZIP/Zip64 format preflight aligned with POI use, explicit pin.',
    'commons-csv': 'Required mature CSV grammar engine for the always-available CSV facade; explicit pin.',
    'tomcat-embed-core': 'Test-only real Servlet/ERROR/streaming integration; no runtime container choice.',
    'spring-boot-starter-test': 'Test aggregation: JUnit/AssertJ/Mockito/Spring test dependencies.',
    'spring-boot-web-server': 'Test-only Boot4 moved web server abstractions for actual container fixtures.',
    'spring-boot-tomcat': 'Test-only Boot4 Tomcat integration for actual container fixtures.',
    'spring-boot-webmvc': 'Test-only Boot4 MVC integration for actual container fixtures.',
    'junit-jupiter-params': 'Parameterized boundary/platform contracts discovered by JUnit.',
    'logback-classic': 'Test-only actual logging/MDC behavior; application remains backend owner.',
    'hibernate-validator': 'Test-only real validation and missing-provider combinations.',
    'archunit-junit6': 'JUnit6 architecture engine; explicit platform-compatible version and discovery guard.',
    'server-facility': 'This selected 0.2 Boot4/Jackson3 ordinary runtime; mutable coordinate must bind actual jar digest.',
    'spring-boot-starter-restclient': 'Application-owned standard RestClient builder and finite outbound HTTP policy.',
    'micrometer-tracing-bridge-brave': 'Partner test observation bridge for actual standard outbound spans.',
    'org.jacoco.agent': 'Test-only offline instrumentation runtime; isolated child dumps do not substitute for parent coverage.',
    'spring-boot-starter-webmvc': 'Executable application Servlet/MVC server ownership.',
    'spring-boot-starter-security-oauth2-resource-server': 'Standard verified JWT issuer/audience authentication and method authority.',
    'context-propagation': 'Standard context capture/restoration across application-owned async work.',
    'spring-boot-starter-jdbc': 'Application-owned single DataSource/JdbcClient/transaction manager graph.',
    'spring-boot-starter-flyway': 'Single explicit database schema initializer with required migration history.',
    'flyway-database-postgresql': 'Mature PostgreSQL Flyway engine rather than a second schema owner.',
    'postgresql': 'Runtime PostgreSQL JDBC driver with finite connection/socket/cancel/statement budgets.',
    'spring-boot-starter-actuator': 'Host-owned observation and application lifecycle instrumentation.',
    'spring-boot-starter-zipkin': 'Standard tracing integration; export remains explicitly application-controlled.',
}
PLUGIN_REASONS = {
    'maven-enforcer-plugin': 'JDK25/Maven3.10.0 and required coverage input gates.',
    'maven-clean-plugin': 'Reproducible removal of project-owned build output.',
    'maven-resources-plugin': 'Deterministic resource copying and declared encoding.',
    'maven-install-plugin': 'Ordinary jar install into the verification-owned isolated repository; no publish.',
    'maven-help-plugin': 'Actual effective-model evidence, including inherited versions.',
    'maven-compiler-plugin': 'Release25, parameter names and explicit annotation processor paths.',
    'maven-surefire-plugin': 'Actual JUnit/ArchUnit discovery and positive/negative test reports.',
    'jacoco-maven-plugin': 'Executed coverage evidence and unchanged instruction/line88%, branch75% gates.',
    'maven-jar-plugin': 'Ordinary artifact packaging with inherited effective output timestamp.',
    'maven-dependency-plugin': 'Dependency graph/classpath evidence and fail-on-warning declared usage guard.',
    'maven-deploy-plugin': 'Maven3.10.0 Super POM default; inventoried but deploy is not run or authorized.',
    'maven-site-plugin': 'Maven3.10.0 Super POM default; inventoried, not an all/platform execution.',
    'spring-boot-maven-plugin': 'Executable application repackage, separate from the ordinary runtime/partner jars.',
}
PROJECTS = {
    'runtime': ('pom.xml', ''),
    'partner': ('examples/partner-aggregation/pom.xml', 'partner'),
    'secured-api': ('templates/secured-api/pom.xml', 'template'),
    'assembly-workflow': ('examples/assembly-workflow/overlay/pom.xml', 'workflow'),
}


def git_bytes(repo, source, path):
    return subprocess.check_output(['git', '-C', str(repo), 'show', source + ':' + path])


def text(element, key, default=''):
    return element.findtext('m:' + key, default=default, namespaces=NS)


def gav(element):
    return ':'.join(text(element, key) for key in ('groupId', 'artifactId', 'version'))


def dep(element):
    return {'group': text(element, 'groupId'), 'artifact': text(element, 'artifactId'),
            'version': text(element, 'version'), 'type': text(element, 'type', 'jar'),
            'classifier': text(element, 'classifier'), 'scope': text(element, 'scope', 'compile'),
            'optional': text(element, 'optional', 'false') == 'true'}


def dep_key(row):
    return row['group'], row['artifact'], row['type'], row['classifier']


def model(repo, source, evidence, role):
    source_path, directory = PROJECTS[role]
    declared_bytes = git_bytes(repo, source, source_path)
    declared = ET.fromstring(declared_bytes)
    model_path = evidence / directory / 'effective-pom.xml'
    effective = ET.parse(model_path).getroot()
    properties = {child.tag.rsplit('}', 1)[-1]: child.text or ''
                  for child in effective.find('m:properties', NS)}
    source_properties = {child.tag.rsplit('}', 1)[-1]: child.text or ''
                         for child in declared.find('m:properties', NS)}
    if gav(declared) != gav(effective):
        raise ValueError(('effective model GAV does not match source', role, gav(declared), gav(effective)))
    managed = {dep_key(row): row for item in effective.findall('m:dependencyManagement/m:dependencies/m:dependency', NS)
               for row in [dep(item)]}
    resolved = {dep_key(row): row for item in effective.findall('m:dependencies/m:dependency', NS) for row in [dep(item)]}
    dependencies = []
    for item in declared.findall('m:dependencies/m:dependency', NS):
        declaration = dep(item); actual = resolved[dep_key(declaration)]
        if not actual['version'] or '${' in actual['version']:
            raise ValueError(('unresolved direct dependency', role, actual))
        version = declaration['version']
        origin = ('project property ' + version if '${' in version else 'explicit literal') if version else 'imported Spring Boot dependency management'
        if version and re.fullmatch(r'\$\{[^}]+\}', version):
            expected = source_properties[version[2:-1]]
            if '${' not in expected and actual['version'] != expected:
                raise ValueError(('effective direct version differs from declared property', role, actual))
        elif version and actual['version'] != version:
            raise ValueError(('effective direct version differs from literal', role, actual))
        dependencies.append({'declaration': declaration, 'effective': actual, 'versionSource': origin,
                             'rationale': REASONS[actual['artifact']]})
    declared_plugins = {text(item, 'artifactId'): item for item in declared.findall('m:build/m:plugins/m:plugin', NS)}
    plugins = []
    for item in effective.findall('m:build/m:plugins/m:plugin', NS):
        artifact = text(item, 'artifactId'); declaration = declared_plugins.get(artifact)
        version = text(item, 'version')
        if not version or '${' in version: raise ValueError(('unresolved plugin', role, artifact))
        plugins.append({'group': text(item, 'groupId', 'org.apache.maven.plugins'), 'artifact': artifact,
                        'version': version, 'declarationVersion': None if declaration is None else text(declaration, 'version'),
                        'versionSource': 'Maven3.10.0 Super POM default' if declaration is None else 'explicit source POM/property',
                        'rationale': PLUGIN_REASONS[artifact],
                        'executions': [{'id': text(execution, 'id'), 'phase': text(execution, 'phase'),
                                        'goals': [goal.text for goal in execution.findall('m:goals/m:goal', NS)]}
                                       for execution in item.findall('m:executions/m:execution', NS)]})
    processors = []
    for item in effective.findall('m:build/m:plugins/m:plugin/m:configuration/m:annotationProcessorPaths/m:path', NS):
        row = dep(item); inherited = not row['version']
        if inherited: row['version'] = managed[dep_key(row)]['version']
        if not row['version'] or '${' in row['version']: raise ValueError(('unresolved processor', row))
        processors.append({'group': row['group'], 'artifact': row['artifact'], 'version': row['version'],
                           'versionSource': 'effective dependency management; processor path has no version' if inherited else 'explicit processor path version',
                           'rationale': 'Explicit JDK25 processor admission; ' + ('Lombok generation' if row['artifact'] == 'lombok' else 'Boot configuration metadata')})
    graph = evidence / directory / 'dependency-tree.txt'
    return {'role': role, 'sourcePom': source_path, 'sourcePomSha256': hashlib.sha256(declared_bytes).hexdigest(),
            'effectivePom': str(model_path), 'effectivePomSha256': sha(model_path), 'gav': gav(effective),
            'buildOutputTimestamp': properties.get('project.build.outputTimestamp'),
            'dependencyGraph': str(graph), 'dependencyGraphSha256': sha(graph),
            'directDependencies': dependencies, 'processors': processors, 'plugins': plugins}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository', type=Path, default=Path('.'))
    parser.add_argument('--source', required=True, help='Exact 40-character source found in the all revision log')
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--jar-sha256', required=True)
    parser.add_argument('--all-evidence', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--javap', type=Path, required=True)
    parser.add_argument('--mode', choices=['preparation', 'candidate'], required=True)
    args = parser.parse_args()
    if not re.fullmatch('[0-9a-f]{40}', args.source): raise ValueError('Exact source SHA required')
    if args.mode == 'candidate':
        head = subprocess.check_output(['git', '-C', str(args.repository), 'rev-parse', 'HEAD'], text=True).strip()
        dirty = subprocess.check_output(['git', '-C', str(args.repository), 'status', '--porcelain'], text=True)
        if head != args.source or dirty:
            raise ValueError('Candidate extraction requires the exact source HEAD and a clean checkout')
    if not re.fullmatch('[0-9a-f]{64}', args.jar_sha256) or sha(args.jar) != args.jar_sha256:
        raise ValueError('Artifact digest mismatch; do not relabel a different jar')
    revision_files = list(args.all_evidence.glob('*-revision.log'))
    if len(revision_files) != 1 or revision_files[0].read_text(encoding='utf-8').strip() != args.source:
        raise ValueError('Evidence source differs from --source')
    with zipfile.ZipFile(args.jar) as jar:
        packaged = jar.read('META-INF/maven/cn.code91/server-facility/pom.xml')
    if packaged != git_bytes(args.repository, args.source, 'pom.xml'):
        raise ValueError('Packaged runtime POM bytes differ from the declared source')
    prior = json.loads(git_bytes(args.repository, args.source, 'docs/verification/ticket31/api-ticket31-inventory.json'))
    policy = json.loads(git_bytes(args.repository, args.source, 'docs/verification/ticket31/runtime-package-map.json'))
    baseline, current = prior['baseline'], inspect(args.jar, args.javap)
    difference = compare(baseline, current)
    changes = {row['class']: row for row in difference['commonClassChanges']}
    files = {}
    def reference(path):
        if path not in files:
            data = git_bytes(args.repository, args.source, path)
            files[path] = {'path': path, 'sourceCommit': args.source, 'sha256': hashlib.sha256(data).hexdigest()}
        return path
    packages = []
    for original in policy['packages']:
        row = {key: value for key, value in original.items() if key not in {'types', 'currentPublicProtectedTypes', 'baselinePublicProtectedTypes'}}
        package = row['package']
        row['baselinePublicProtectedTypes'] = baseline['packages'].get(package, 0)
        row['currentPublicProtectedTypes'] = current['packages'].get(package, 0)
        row['types'] = []
        for name, item in current['classes'].items():
            if name.split('.')[3] == package:
                path = reference('src/main/java/' + name.split('$')[0].replace('.', '/') + '.java')
                row['types'].append({'name': name, 'sourcePath': path, 'newPublicType': name not in baseline['classes'],
                                     'declaredMembers': len(item['members']), 'delta': changes.get(name)})
        for key in ('documents', 'consumers', 'primaryTests'):
            for path in row[key]: reference(path)
        packages.append(row)
    if set(current['packages']) != {row['package'] for row in packages}:
        raise ValueError('Package policy coverage differs from actual jar; manual disposition required')
    stable_inputs = {'runtimeSourceTree': subprocess.check_output(['git', '-C', str(args.repository), 'rev-parse', args.source + ':src/main'], text=True).strip(),
                     'runtimePomSha256': hashlib.sha256(packaged).hexdigest()}
    api = {'schemaVersion': 1, 'mode': args.mode, 'status': 'extraction only; final candidate gates are separate',
           'extractorSha256': sha(Path(__file__)), 'stableProductionInputs': stable_inputs,
           'sourceCommit': args.source, 'candidateBinding': None if args.mode == 'preparation' else {'sourceCommit': args.source, 'runtimeSha256': args.jar_sha256},
           'baselineSource': prior['baselineSource'], 'policySource': policy['referenceSource'],
           'methodLimitations': ['Declared members only; not full inherited/default method resolution',
                                'Generic declarations and parents are recorded; descriptor equality is not source/binary compatibility',
                                'No certification of annotation defaults/constants/serialVersionUID/overload ambiguity',
                                'Behavior, wire and persisted protocols require their named executed consumer tests'],
           'baseline': baseline, 'current': current, 'difference': difference,
           'packageCount': len(packages), 'packages': packages, 'webSubgroups': policy['webSubgroups'],
           'referencedFiles': sorted(files.values(), key=lambda row: row['path'])}
    models = [model(args.repository, args.source, args.all_evidence, role) for role in PROJECTS]
    root = models[0]
    if len(root['directDependencies']) != 33 or len(root['processors']) != 2 or len(root['plugins']) != 12:
        raise ValueError('Declared dependency/processor/plugin frontier changed; update the audited ledger')
    dependencies = {'schemaVersion': 1, 'mode': args.mode, 'status': 'effective-model extraction; no new build or test run',
                    'extractorSha256': sha(Path(__file__)), 'stableProductionInputs': stable_inputs,
                    'sourceCommit': args.source, 'candidateBinding': api['candidateBinding'],
                    'allEvidence': str(args.all_evidence), 'runtimeSha256': args.jar_sha256,
                    'projects': models, 'ciActions': 'Separately verified/pinned by the CI owner; not Maven dependencies or plugins',
                    'transitiveGraphPolicy': 'Full resolved graphs are retained at the exact paths/digests; no transitive upgrade inferred from declared versions'}
    args.output.mkdir(parents=True, exist_ok=True)
    for filename, payload in [('package-api-inventory.json', api), ('dependency-inventory.json', dependencies)]:
        (args.output / filename).write_text(json.dumps(payload, ensure_ascii=False, indent=2) + '\n', encoding='utf-8', newline='\n')
    print(json.dumps({'source': args.source, 'mode': args.mode, 'packages': len(packages), 'types': current['accessibleClasses'],
                      'members': sum(len(row['members']) for row in current['classes'].values()),
                      'removedOldDescriptors': sum(len(row['removedDeclaredMembers']) for row in difference['commonClassChanges']),
                      'sameDescriptorDeclarationChanges': sum(len(row['sameDescriptorChangedDeclaration']) for row in difference['commonClassChanges']),
                      'rootDirectDependencies': len(root['directDependencies']), 'rootProcessors': len(root['processors']),
                      'rootEffectivePlugins': len(root['plugins'])}, ensure_ascii=False))


if __name__ == '__main__':
    main()
