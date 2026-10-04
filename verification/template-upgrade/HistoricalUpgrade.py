#!/usr/bin/env python3
"""Fixed historical three-file template upgrade demonstrator. Python3 standard library and Git.

Exact source/blob guards preserve customized business and unrelated configuration.
This is not a general updater and does not claim that historical commits were releases.
"""
import argparse, hashlib, json, os, pathlib, re, shutil, subprocess, tempfile, sys, zipfile, xml.etree.ElementTree as ET

BEFORE='4a5ad5d2513ccf7d87f2feeb95056551b4b0ef15'
AFTER='307dae62ea63fce19c1630dfaf0fa0b811869a71'
TREES={BEFORE:'24a021b3640e430279edd551415df771cf4f0543',AFTER:'c1ec08c15699da507463179dc2b4b7775d0fbfa3'}
RUNTIME_TREE='e670e234169987ce0ae5ccde22af226e2f230790'
RUNTIME_POM='e4613c12e7632ed6b371df9414844aba2225308e'
RUNTIME_SHA='e97cedb5ab07ac9cabe638bf001ded6a5bb2f22ac521351fe09755d3ebc59595'
PATCH_SHA='9fab9045ce45106fd0296bd68f1826f3bd3ce51961c877e1b3aaf6fb89d58a5e'
SELECTED_RUNTIME={'runtime.mode':'historical-same-runtime','runtime.coordinate':'cn.code91:server-facility:0.1.0-SNAPSHOT',
 'runtime.source-commit':BEFORE,'runtime.source-tree':RUNTIME_TREE,'runtime.pom-blob':RUNTIME_POM,'runtime.jar-sha256':RUNTIME_SHA}
OWNED={
 'src/main/java/com/example/api/notes/DatabaseConfiguration.java':('356a585076e7ba15afc4b0b5f01931ca88ba25fb','c3056ce09e7027fe0a363c7d69eeba003f649de1'),
 'src/test/java/com/example/api/DatabaseConfigurationTest.java':('f82dd1b15b55074e6e3c10fe14dbccd87d8354ad','ed3fbd92fe80e2c2f3fc14599829373ddcd9d2bc'),
 'README.md':('f40e1e74a4d2bc3268c20aa5674cd9c20b2a57ef','b0220f3ef6a41b0294cc6f77d8b08a6e49ba31f4')}
ORIGIN='template-origin.properties'
PROPERTIES='src/main/resources/application.properties'
CUSTOM_JAVA='src/main/java/com/example/api/customer/CustomerStatusController.java'
CUSTOM_TEST='src/test/java/com/example/api/CustomerUpgradeTest.java'
OLD_QUERY=b'spring.jdbc.template.query-timeout=1500ms\n'
NEW_QUERY=b'spring.jdbc.template.query-timeout=1s\n'

CONTROLLER='''package com.example.api.customer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class CustomerStatusController {
    private final String label;
    private final String application;
    public CustomerStatusController(@Value("${app.customer.label}") String label,
            @Value("${spring.application.name}") String application) {
        this.label = label; this.application = application;
    }
    @GetMapping("/api/greeting/customer")
    public CustomerStatus status() { return new CustomerStatus(label, application); }
    public record CustomerStatus(String label, String application) {}
}
'''
TEST='''package com.example.api;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class CustomerUpgradeTest {
    @Test void customBusinessAndEffectiveBudgetSurviveRestartWithThePinnedRuntime() throws Exception {
        var origin = new Properties();
        try (var input = Files.newBufferedReader(Path.of("template-origin.properties"))) { origin.load(input); }
        Path runtime = Path.of(cn.code91.facility.result.Result.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String runtimeVersion = origin.getProperty("runtime.coordinate").split(":")[2];
        assertThat(runtime.getFileName().toString()).isEqualTo("server-facility-" + runtimeVersion + ".jar");
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(runtime))))
                .isEqualTo(origin.getProperty("runtime.jar-sha256"));
        String database = Postgres.freshUrl();
        try (var issuer = new TestIssuer()) {
            for (int restart = 0; restart < 2; restart++) {
                try (var app = new RunningApp(issuer, "--spring.datasource.url=" + database)) {
                    assertThat(app.context.getBean(JdbcTemplate.class).getQueryTimeout()).isEqualTo(1);
                    assertThat(app.get("/api/greeting/customer", null).statusCode()).isEqualTo(401);
                    assertThat(app.get("/api/greeting/customer", issuer.token("a", Map.of("scope", "notes:read"), Set.of())).statusCode()).isEqualTo(403);
                    var response = app.get("/api/greeting/customer", issuer.token());
                    assertThat(response.statusCode()).isEqualTo(200);
                    var body = JsonMapper.builder().build().readTree(response.body());
                    assertThat(body.path("label").asString()).isEqualTo("orders-north");
                    assertThat(body.path("application").asString()).isEqualTo("customer-notes");
                }
            }
        }
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @org.junit.jupiter.api.Timeout(20)
    void configuredJdbcBudgetActuallyCancelsSlowSqlAndRemainsUsable(boolean virtual) throws Exception {
        try (var issuer = new TestIssuer(); var app = new RunningApp(issuer,
                "--spring.datasource.url=" + Postgres.freshUrl(), "--spring.threads.virtual.enabled=" + virtual)) {
            JdbcTemplate jdbc = app.context.getBean(JdbcTemplate.class);
            assertThat(jdbc.getQueryTimeout()).isEqualTo(1);
            assertThat(jdbc.queryForObject("show statement_timeout", String.class)).isEqualTo("2s");
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            long start = System.nanoTime();
            assertThatThrownBy(() -> jdbc.execute("select pg_sleep(1.6)"))
                    .isInstanceOf(org.springframework.dao.QueryTimeoutException.class)
                    .hasRootCauseInstanceOf(java.sql.SQLException.class);
            long millis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(jdbc.queryForObject("select 42", Integer.class)).isEqualTo(42);
            assertThat(app.get("/api/greeting/customer", issuer.token()).statusCode()).isEqualTo(200);
            System.out.println("JDBC_STAGE_CANCELLED configured=1s server=2s slow=1.6s observedMs=" + millis + " virtual=" + virtual);
        }
    }
    @Test void explicitFractionalOverrideFollowsTheDeclaredHistoricalRevision() throws Exception {
        String phase = System.getProperty("upgrade.phase");
        assertThat(phase).isIn("before", "after");
        try (var issuer = new TestIssuer()) {
            if (phase.equals("before")) {
                try (var app = new RunningApp(issuer, "--spring.jdbc.template.query-timeout=1500ms")) {
                    assertThat(app.context.getBean(JdbcTemplate.class).getQueryTimeout()).isEqualTo(1);
                }
            } else {
                assertThatThrownBy(() -> {
                    try (var ignored = new RunningApp(issuer, "--spring.jdbc.template.query-timeout=1500ms", "--logging.level.root=OFF")) {}
                }).hasStackTraceContaining("Invalid application database policy");
            }
        }
    }
}
'''

def require(condition,message):
    if not condition: raise ValueError(message)
def digest(data): return hashlib.sha256(data).hexdigest()
def file_digest(path):
    with path.open('rb') as stream: return hashlib.file_digest(stream,'sha256').hexdigest()
def blob(data): return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
def git(repo,*args):
    flags={'creationflags':subprocess.CREATE_NO_WINDOW} if os.name=='nt' else {}
    return subprocess.run(['git','-c','core.autocrlf=false','-c','core.eol=lf','-C',str(repo),*args],check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=30,**flags).stdout

def query_property_span(properties,expected=OLD_QUERY):
    """Locate the one canonical active line; never match a comment or another property's value."""
    matches=[]; offset=0
    for line in properties.splitlines(keepends=True):
        logical=line.rstrip(b'\r\n').lstrip(b' \t\f')
        if logical and not logical.startswith((b'#',b'!')):
            key=re.split(rb'[ \t\f=:]',logical,maxsplit=1)[0]
            require(b'\\' not in key,'escaped property keys are outside the fixed upgrade format')
            trailing_slashes=len(logical)-len(logical.rstrip(b'\\'))
            require(trailing_slashes%2==0,'continued property lines are outside the fixed upgrade format')
        if re.match(rb'^[ \t\f]*spring\.jdbc\.template\.query-timeout(?:[ \t\f=:]|$)',line):
            matches.append((offset,offset+len(line),line))
        offset+=len(line)
    require(len(matches)==1 and matches[0][2]==expected,'expected exactly one canonical query timeout property line')
    return matches[0][:2]

def rewrite_query_property(properties):
    start,end=query_property_span(properties)
    return properties[:start]+NEW_QUERY+properties[end:]

def origin_bytes(commit):
    values={
      'origin.format':'1','origin.basis':'reconstructed-development-checkpoint-not-public-release',
      'template.id':'secured-api','template.source-commit':commit,'template.source-tree':TREES[commit],
      **SELECTED_RUNTIME}
    return ''.join(f'{key}={value}\n' for key,value in values.items()).encode('ascii')

def history(repo):
    for commit,tree in TREES.items():
        require(git(repo,'rev-parse',commit).decode().strip()==commit,'exact commit mismatch')
        require(git(repo,'rev-parse',commit+':templates/secured-api').decode().strip()==tree,'template tree mismatch')
        require(git(repo,'rev-parse',commit+':src').decode().strip()==RUNTIME_TREE,'runtime source mismatch')
        require(git(repo,'rev-parse',commit+':pom.xml').decode().strip()==RUNTIME_POM,'runtime POM mismatch')
    require(git(repo,'rev-parse',AFTER+'^').decode().strip()==BEFORE,'historical pair is not direct parent/child')
    patch=git(repo,'diff','--binary','--full-index','--relative=templates/secured-api',BEFORE,AFTER,'--','templates/secured-api')
    require(digest(patch)==PATCH_SHA,'historical patch bytes mismatch')
    for path,expected in OWNED.items():
        for index,commit in enumerate([BEFORE,AFTER]):
            require(blob(git(repo,'show',commit+':templates/secured-api/'+path))==expected[index],'owned blob mismatch '+path)
    return patch

def snapshot(app):
    result={}
    for path in sorted(app.rglob('*')):
        if '.git' in path.relative_to(app).parts: continue
        require(not path.is_symlink(),'symbolic link unsupported: '+str(path))
        if path.is_file(): result[path.relative_to(app).as_posix()]=file_digest(path)
    return result

def outside_worktrees(repo,destination):
    for line in git(repo,'worktree','list','--porcelain').decode('utf-8').splitlines():
        if line.startswith('worktree '):
            require(not destination.is_relative_to(pathlib.Path(line[9:]).resolve()),'sample must be outside existing worktrees')
    require(not destination.exists(),'sample destination must be new')

def prepare(repo,app,runtime):
    history(repo); outside_worktrees(repo,app)
    require(runtime.is_file() and file_digest(runtime)==SELECTED_RUNTIME['runtime.jar-sha256'],'actual runtime jar differs from selected runtime identity')
    # Read all original files before making the new sample; reject unexpected git modes.
    files={}
    for entry in git(repo,'ls-tree','-rz',BEFORE+':templates/secured-api').split(b'\0'):
        if not entry: continue
        metadata,name=entry.split(b'\t',1); mode,kind,oid=metadata.decode().split()
        path=name.decode('utf-8'); relative=pathlib.PurePosixPath(path)
        require(kind=='blob' and mode in ('100644','100755') and not relative.is_absolute() and '..' not in relative.parts,'unsupported template entry')
        files[path]=(git(repo,'cat-file','blob',oid),mode)
    properties=files[PROPERTIES][0]
    require(properties.count(b'spring.application.name=secured-api\n')==1 and properties.count(b'spring.jdbc.template.query-timeout=2s\n')==1,'unexpected old properties')
    files[PROPERTIES]=(properties.replace(b'spring.application.name=secured-api\n',b'spring.application.name=customer-notes\n').replace(b'spring.jdbc.template.query-timeout=2s\n',OLD_QUERY)+b'\n# Application-owned customer policy\napp.customer.label=orders-north\n','100644')
    files[CUSTOM_JAVA]=(CONTROLLER.encode('utf-8'),'100644'); files[CUSTOM_TEST]=(TEST.encode('utf-8'),'100644')
    files[ORIGIN]=(origin_bytes(BEFORE),'100644')
    app.mkdir(parents=True)
    for name,(data,mode) in files.items():
        path=app/name; path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes(data)
        if mode=='100755': path.chmod(path.stat().st_mode|0o100)
    return {'sample':str(app),'source':BEFORE,'manifest':snapshot(app),'runtimeJar':str(runtime),'runtime':dict(SELECTED_RUNTIME)}

def preflight(repo,app):
    patch=history(repo)
    require(app.is_dir(),'sample directory missing')
    before=snapshot(app)
    require((app/ORIGIN).read_bytes()==origin_bytes(BEFORE),'wrong source identity or already upgraded')
    for path,expected in OWNED.items(): require(blob((app/path).read_bytes())==expected[0],'modified owned target: '+path)
    properties=(app/PROPERTIES).read_bytes()
    query_property_span(properties)
    require((app/CUSTOM_JAVA).is_file() and (app/CUSTOM_TEST).is_file(),'custom business/test missing')
    return patch,before,properties

def upgrade(repo,app):
    patch,before,properties=preflight(repo,app)
    originals={name:(app/name).read_bytes() for name in [*OWNED,PROPERTIES,ORIGIN]}
    replacements={}
    # Stage the ordinary Git patch on only its exact owned preimages. No sample edit yet.
    with tempfile.TemporaryDirectory(prefix='facility-upgrade-stage-') as temporary:
        stage=pathlib.Path(temporary)
        for name in OWNED:
            target=stage/name; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(originals[name])
        patchfile=stage/'upgrade.patch'; patchfile.write_bytes(patch)
        git(stage,'apply','--check',str(patchfile)); git(stage,'apply',str(patchfile))
        for name,expected in OWNED.items():
            data=(stage/name).read_bytes(); require(blob(data)==expected[1],'postimage mismatch: '+name); replacements[name]=data
    query_start,query_end=query_property_span(properties)
    replacements[PROPERTIES]=rewrite_query_property(properties)
    replacements[ORIGIN]=origin_bytes(AFTER) # lineage is written last
    require(snapshot(app)==before,'sample changed during preflight; exclusive working directory required')
    try:
        for name,data in replacements.items(): (app/name).write_bytes(data)
    except BaseException as first:
        # This is best-effort restoration, not a filesystem crash-atomic transaction.
        for name,data in originals.items():
            try: (app/name).write_bytes(data)
            except BaseException as restoration: first.add_note('restore failed '+name+': '+str(restoration))
        raise
    after=snapshot(app)
    changed={name for name in before.keys()|after.keys() if before.get(name)!=after.get(name)}
    require(changed==set(replacements),'unexpected changed files')
    actual_properties=(app/PROPERTIES).read_bytes()
    require(actual_properties[:query_start]==properties[:query_start]
        and actual_properties[query_start:query_start+len(NEW_QUERY)]==NEW_QUERY
        and actual_properties[query_start+len(NEW_QUERY):]==properties[query_end:],'unrelated property bytes changed')
    require(all(after[name]==before[name] for name in (CUSTOM_JAVA,CUSTOM_TEST)),'custom business bytes changed')
    return {'source':BEFORE,'target':AFTER,'runtime':dict(SELECTED_RUNTIME),'patchSha256':PATCH_SHA,'changed':sorted(changed),'before':before,'after':after}

def refusals(repo,app):
    preflight(repo,app)
    results=[]
    with tempfile.TemporaryDirectory(prefix='facility-upgrade-refusals-') as temporary:
        for case in ['wrong-source','modified-owned','reapply']:
            target=pathlib.Path(temporary)/case; shutil.copytree(app,target)
            if case=='wrong-source':
                marker=(target/ORIGIN).read_bytes(); (target/ORIGIN).write_bytes(marker.replace(BEFORE.encode(),b'0'*40,1))
            elif case=='modified-owned':
                path=target/next(iter(OWNED)); path.write_bytes(path.read_bytes()+b'\n// customer modification\n')
            else: upgrade(repo,target)
            before=snapshot(target)
            try: upgrade(repo,target)
            except ValueError as refusal:
                require(snapshot(target)==before,'refusal changed sample bytes: '+case)
                results.append({'case':case,'result':'REFUSED_UNCHANGED','reason':str(refusal),'manifest':before})
            else: raise AssertionError('must refuse '+case)
    return results

def select_runtime(repo,manifest_path,jar):
    # Optional33 requalification seam: explicit identity, never a silent SNAPSHOT substitution.
    require(jar is not None and jar.is_file(),'candidate mode requires --runtime-jar')
    require(manifest_path.stat().st_size<=16384,'candidate manifest unexpectedly large')
    value=json.loads(manifest_path.read_text(encoding='utf-8'))
    require(set(value)=={'sourceCommit','sourceTree','pomBlob','coordinate','sha256'},'candidate manifest fields mismatch')
    require(file_digest(jar)==value['sha256'],'candidate jar checksum mismatch')
    commit=value['sourceCommit']
    require(git(repo,'rev-parse',commit).decode().strip()==commit,'candidate requires full source commit')
    require(git(repo,'rev-parse',commit+':src').decode().strip()==value['sourceTree'],'candidate source tree mismatch')
    require(git(repo,'rev-parse',commit+':pom.xml').decode().strip()==value['pomBlob'],'candidate POM blob mismatch')
    pom=ET.fromstring(git(repo,'show',commit+':pom.xml'))
    ns={'m':'http://maven.apache.org/POM/4.0.0'}
    coordinate=':'.join(pom.findtext('m:'+field,namespaces=ns) or '' for field in ['groupId','artifactId','version'])
    require(coordinate==value['coordinate'] and coordinate.startswith('cn.code91:server-facility:'),'candidate source coordinate mismatch')
    with zipfile.ZipFile(jar) as archive:
        properties=archive.read('META-INF/maven/cn.code91/server-facility/pom.properties').decode('ascii')
    metadata=dict(line.split('=',1) for line in properties.splitlines() if '=' in line and not line.startswith('#'))
    require(':'.join(metadata[key] for key in ['groupId','artifactId','version'])==coordinate,'candidate artifact coordinate mismatch')
    SELECTED_RUNTIME.update({'runtime.mode':'candidate-requalification','runtime.coordinate':coordinate,
      'runtime.source-commit':commit,'runtime.source-tree':value['sourceTree'],'runtime.pom-blob':value['pomBlob'],'runtime.jar-sha256':value['sha256']})

def main():
    sys.stdout.reconfigure(encoding='utf-8'); sys.stderr.reconfigure(encoding='utf-8')
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation',choices=['check-history','prepare','check','upgrade','refusals'])
    parser.add_argument('--repo',required=True,type=pathlib.Path)
    parser.add_argument('--app',type=pathlib.Path)
    parser.add_argument('--runtime-jar',type=pathlib.Path)
    parser.add_argument('--runtime-manifest',type=pathlib.Path,help='Optional explicit current-candidate identity for33 requalification; --runtime-jar then required for every operation')
    parser.add_argument('--evidence',type=pathlib.Path,help='JSON output outside the sample directory')
    args=parser.parse_args(); repo=args.repo.resolve()
    app=args.app.resolve() if args.app else None
    if args.runtime_manifest: select_runtime(repo,args.runtime_manifest.resolve(),args.runtime_jar.resolve() if args.runtime_jar else None)
    if args.operation=='check-history': result={'before':BEFORE,'after':AFTER,'patchSha256':digest(history(repo)),'scope':'read-only history validation; no sample prepared/applied/tested'}
    else:
        require(app is not None,'--app required')
        if args.evidence: require(not args.evidence.resolve().is_relative_to(app),'evidence must be outside sample')
        if args.operation=='prepare':
            require(args.runtime_jar is not None,'--runtime-jar required'); result=prepare(repo,app,args.runtime_jar.resolve())
        elif args.operation=='check':
            _,manifest,properties=preflight(repo,app)
            start,end=query_property_span(properties)
            result={'preflight':'PASS','source':BEFORE,'manifest':manifest,'propertiesWithoutQuerySha256':digest(properties[:start]+properties[end:])}
        elif args.operation=='upgrade': result=upgrade(repo,app)
        else: result=refusals(repo,app)
    output=json.dumps(result,ensure_ascii=False,indent=2)+'\n'
    if args.evidence:
        args.evidence.parent.mkdir(parents=True,exist_ok=True); args.evidence.write_text(output,encoding='utf-8',newline='\n')
    print(output)
if __name__=='__main__': main()
