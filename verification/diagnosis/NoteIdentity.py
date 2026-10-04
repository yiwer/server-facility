"""Four finite Linux diagnosis cases; never a candidate qualification or repair.

Raw command logs / Surefire reports stay runner-private. Only fixed schema,
source-validated test names/lines, integer observations and hashes are published.
"""
from __future__ import annotations

import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import time
import traceback
import xml.etree.ElementTree as ET

BASELINE = "ee57dfae21419890f5cf7de6ccd16491ba80f93c"
TARGET = "com.example.api.NoteIdentityStorageTest"
METHODS = {
    "storageRejectsInvalidUnicodeBeforeJdbcAndUsesActualUtf8Bytes": {15, 16, 22, 34, 35, 36},
    "aLegalOpaqueSignedSubjectDoesNotBecomeTooLargeForTheMembershipIndex": {47, 49, 53, 54},
}
TEST_REL = Path("src/test/java/com/example/api/NoteIdentityStorageTest.java")
HELPER_REL = Path("src/test/java/com/example/api/NotesHttpTest.java")
TEST_LF_SHA256 = "4639d29cd4ab388aa85f1d46ca65d5c758b293d3d59545fe6b745bf63e608a28"
HELPER_LF_SHA256 = "ecca8031970d0a260c9b9848b0b5b1bbff96b5dc626bdd4e86644098a9b9fd2e"
PREFIX = ["BusinessBoundaryTest", "DatabaseConfigurationTest", "MigrationHttpTest", "NoteCommandsHttpTest", "NoteIdentityStorageTest"]
# Source-reviewed expanded counts, cross-checked against ticket31's actual workflow XML.
# Non-target parameter displays remain private; only fixed suite names/counts are exported.
SUITE_EXPECTED = dict(zip(PREFIX, [2, 6, 8, 6, 2]))
CASES = [
    {"id": "single-original", "classes": ["NoteIdentityStorageTest"], "trace": None, "seconds": 180},
    {"id": "same-fork-prefix", "classes": PREFIX, "trace": None, "seconds": 300},
    {"id": "fixed-trace-clear", "classes": ["NoteIdentityStorageTest"], "trace": "0123456789abcdef0123456789abcdef", "seconds": 180},
    {"id": "fixed-trace-bad", "classes": ["NoteIdentityStorageTest"], "trace": "0123456789abcdef0123456789baddef", "seconds": 180},
]
EVENT_KEYS = {"status", "traceMatched", "traceHasBad", "bodyHasBad", "outsideTraceHasBad", "invalidActorCode"}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def lf_sha(path: Path) -> str:
    return sha(path.read_text(encoding="utf-8").encode())


def now() -> str:
    return dt.datetime.now(dt.timezone.utc).isoformat()


def save(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")


def annotation(title: str, value) -> None:
    # Only callers' fixed titles + already allowlisted derived structures reach this boundary.
    text = json.dumps(value, ensure_ascii=True).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print("::notice title=" + title + "::" + text, flush=True)


def git(root: Path, *args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=root, text=True, encoding="utf-8").strip()


def run_command(command: list[str], cwd: Path, private: Path, seconds: int, env: dict) -> dict:
    start = now()
    began = time.monotonic()
    private.parent.mkdir(parents=True, exist_ok=True)
    with private.open("wb") as log:
        process = subprocess.Popen(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT,
                                   start_new_session=True)
        timed_out = 0
        try:
            code = process.wait(timeout=seconds)
        except subprocess.TimeoutExpired:
            timed_out = 1
            os.killpg(process.pid, signal.SIGTERM)
            try:
                code = process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid, signal.SIGKILL)
                code = process.wait(timeout=5)
    return {"command": command, "cwd": str(cwd), "started": start, "finished": now(),
            "elapsedMilliseconds": int((time.monotonic() - began) * 1000), "exit": code,
            "timeout": timed_out, "secondsBudget": seconds, "rawLogSha256": sha(private.read_bytes()),
            "rawLogRetention": "runner-private-only; not in durable public artifact"}


def control_helper(original: str, trace: str) -> str:
    assert re.fullmatch(r"[0-9a-f]{32}", trace)
    needle = "        return app.client.send(request.build(), HttpResponse.BodyHandlers.ofString());"
    assert original.count(needle) == 1
    # The original test stays byte-exact. Only this copied send helper supplies a fixed
    # standard W3C header. Record integers without body, token, header or arbitrary text.
    replacement = r'''        request.header("traceparent", "00-TRACE-1234567890abcdef-01");
        var diagnosticResponse = app.client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (method.equals("POST") && path.equals("/api/workspaces")) {
            var diagnosticBody = diagnosticResponse.body();
            var diagnosticMatcher = java.util.regex.Pattern.compile("\\\"traceId\\\"\\s*:\\s*\\\"([0-9a-f]{32})\\\"").matcher(diagnosticBody);
            boolean diagnosticFound = diagnosticMatcher.find();
            String diagnosticTrace = diagnosticFound ? diagnosticMatcher.group(1) : "";
            String diagnosticOutside = diagnosticFound
                    ? diagnosticBody.substring(0, diagnosticMatcher.start()) + diagnosticBody.substring(diagnosticMatcher.end())
                    : diagnosticBody;
            String diagnosticEvent = "{\"status\":" + diagnosticResponse.statusCode()
                    + ",\"traceMatched\":" + (diagnosticTrace.equals("TRACE") ? 1 : 0)
                    + ",\"traceHasBad\":" + (diagnosticTrace.contains("bad") ? 1 : 0)
                    + ",\"bodyHasBad\":" + (diagnosticBody.contains("bad") ? 1 : 0)
                    + ",\"outsideTraceHasBad\":" + (diagnosticOutside.contains("bad") ? 1 : 0)
                    + ",\"invalidActorCode\":" + ("invalid_actor".equals(JSON.readTree(diagnosticBody).path("code").asString()) ? 1 : 0) + "}\n";
            java.nio.file.Files.writeString(java.nio.file.Path.of("target", "identity-diagnosis.jsonl"), diagnosticEvent,
                    java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        }
        return diagnosticResponse;'''.replace("TRACE", trace)
    return original.replace(needle, replacement)


def summarize_reports(application: Path, allowed: dict[str, set[str]]) -> dict:
    result = {"suites": [], "targetMethods": [], "xmlSha256": {}, "tests": 0, "failures": 0,
              "errors": 0, "skipped": 0, "unrecognizedCases": 0, "controlEvents": [],
              "forkCommandSha256": [], "databaseDrops": 0, "failedDatabaseDrops": 0}
    reports = application / "target/surefire-reports"
    for path in sorted(reports.glob("TEST-*.xml")):
        doc = ET.parse(path).getroot()
        suite = doc.get("name", "")
        if suite not in allowed:
            result["unrecognizedCases"] += 1
            continue
        counts = {key: int(doc.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}
        for key, value in counts.items():
            result[key] += value
        result["suites"].append({"suite": suite, **counts})
        result["xmlSha256"][suite] = sha(path.read_bytes())
        for prop in doc.findall("properties/property"):
            if prop.get("name") == "sun.java.command":
                digest = sha(prop.get("value", "").encode())
                if digest not in result["forkCommandSha256"]:
                    result["forkCommandSha256"].append(digest)
        for case in doc.findall("testcase"):
            if suite != TARGET:
                continue
            method = case.get("name", "").removesuffix("()")
            if method not in allowed[suite]:
                result["unrecognizedCases"] += 1
                continue
            failures = case.findall("failure") + case.findall("error")
            # No exception message, cause, body, type name, stack or XML can escape here.
            item = {"method": method, "outcome": 1 if failures else (2 if case.find("skipped") is not None else 0),
                    "assertionFailure": int(any(x.get("type") == "java.lang.AssertionError" for x in failures)),
                    "sourceLines": [], "expectedStatus": [], "actualStatus": [], "unknownSourceLineCount": 0}
            for failure in failures:
                text = (failure.text or "") + "\n" + failure.get("message", "")
                positions = re.findall(r"\bat com\.example\.api\.NoteIdentityStorageTest\." + re.escape(method)
                                       + r"\(NoteIdentityStorageTest\.java:(\d+)\)", text)
                for raw in positions:
                    line = int(raw)
                    if line in METHODS[method]:
                        if line not in item["sourceLines"]:
                            item["sourceLines"].append(line)
                    else:
                        item["unknownSourceLineCount"] += 1
                # HTTP status is exposed only at the three known status-assertion lines.
                if set(item["sourceLines"]) & {34, 49, 53}:
                    for expected, actual in re.findall(r"expected: ([1-5]\d{2})\s+but was: ([1-5]\d{2})(?:\s|$)", text):
                        item["expectedStatus"].append(int(expected))
                        item["actualStatus"].append(int(actual))
            result["targetMethods"].append(item)
    events = application / "target/identity-diagnosis.jsonl"
    if events.exists():
        for line in events.read_text(encoding="utf-8").splitlines():
            event = json.loads(line)
            if set(event) != EVENT_KEYS or any(type(value) is not int for value in event.values()):
                raise ValueError("Invalid diagnostic event schema")
            if not 100 <= event["status"] <= 599 or any(event[key] not in (0, 1) for key in EVENT_KEYS - {"status"}):
                raise ValueError("Invalid diagnostic event value")
            result["controlEvents"].append(event)
    drops = application / "target/postgres-scope-cleanup.jsonl"
    if drops.exists():
        for line in drops.read_text(encoding="utf-8").splitlines():
            event = json.loads(line)
            if type(event.get("success")) is not bool:
                raise ValueError("Invalid cleanup metric")
            result["databaseDrops"] += 1
            result["failedDatabaseDrops"] += int(not event["success"])
    return result


def allowed_methods(application: Path, classes: list[str]) -> dict[str, set[str]]:
    result = {}
    for name in classes:
        source = (application / "src/test/java/com/example/api" / (name + ".java")).read_text(encoding="utf-8")
        result["com.example.api." + name] = set(re.findall(r"@Test\s+void\s+(\w+)\(", source)) if name == "NoteIdentityStorageTest" else set()
    assert result[TARGET] == set(METHODS)
    return result


def execute(root: Path) -> int:
    if sys.platform != "linux":
        raise ValueError("This diagnosis runner is Linux-only")
    private = root / ".verification-results/note-identity/private"
    public = root / ".verification-results/note-identity/public"
    if private.exists() or public.exists():
        raise ValueError("One finite campaign per fresh checkout; evidence already exists")
    private.mkdir(parents=True)
    public.mkdir(parents=True)
    source = git(root, "rev-parse", "HEAD")
    assert re.fullmatch(r"[0-9a-f]{40}", source)
    assert git(root, "status", "--porcelain") == ""
    assert git(root, "merge-base", BASELINE, source) == BASELINE
    assert git(root, "diff", BASELINE, "--", "src", "pom.xml", "templates", "examples", ".mvn", "mvnw", "mvnw.cmd") == ""
    template = root / "templates/secured-api"
    assert lf_sha(template / TEST_REL) == TEST_LF_SHA256
    assert lf_sha(template / HELPER_REL) == HELPER_LF_SHA256
    preimage = {"baselineSource": BASELINE, "diagnosticSource": source, "candidateQualification": False,
                "testLfSha256": TEST_LF_SHA256, "helperLfSha256": HELPER_LF_SHA256,
                "testBytesSha256": sha((template / TEST_REL).read_bytes()), "methods": {k: sorted(v) for k, v in METHODS.items()},
                "plannedCases": CASES, "plannedExecutions": 4, "retryCount": 0,
                "expectedCasesBySuite": SUITE_EXPECTED, "plannedExpandedCases": 30,
                "bootstrapScope": "compile + jar + install goals only; no runtime test/coverage qualification claimed",
                "applicationScope": "clean test, original coverage configuration; verify/report/check not invoked",
                "rawRetention": "Raw logs/XML stay runner-private and disappear with runner. Durable evidence contains only their hashes and allowlisted derived facts."}
    save(public / "preimage-and-plan.json", preimage)
    results = {"baselineSource": BASELINE, "diagnosticSource": source, "candidateQualification": False,
               "commands": [], "cases": [], "harnessComplete": 0}
    annotation("Note identity diagnostic plan", {"baseline": BASELINE, "source": source,
               "testLfSha256": TEST_LF_SHA256, "configurations": 4, "retries": 0})
    campaign_deadline = time.monotonic() + 17 * 60
    env = dict(os.environ)
    env["MAVEN_USER_HOME"] = str(private / "wrapper-home")
    env["REDGATE_DISABLE_TELEMETRY"] = "true"
    env["MAVEN_OPTS"] = "-Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
    repository = private / "repository"
    maven_options = ["-B", "-ntp", "-C", "-s", str(root / "verification/settings.xml"),
                     "-gs", str(root / "verification/settings.xml"), "-Dmaven.repo.local=" + str(repository)]
    java = str(Path(env["JAVA_HOME"]) / "bin/java")
    tools = private / "postgres-18.6"

    def command(name: str, args: list[str], cwd: Path, seconds: int) -> dict:
        remaining = int(campaign_deadline - time.monotonic())
        if remaining < 20:
            raise TimeoutError("Campaign budget exhausted")
        item = {"step": name, **run_command(args, cwd, private / (name + ".log"), min(seconds, remaining - 15), env)}
        results["commands"].append(item)
        save(public / "results.json", results)
        print(json.dumps({"step": name, "exit": item["exit"], "timeout": item["timeout"]}), flush=True)
        return item

    prepare = command("native-prepare", [java, "templates/secured-api/dev/PreparePostgres.java", str(tools)], root, 180)
    if prepare["exit"] != 0:
        annotation("Note identity prerequisite failure", {"stage": "native-prepare", "exit": prepare["exit"], "rawLogSha256": prepare["rawLogSha256"]})
        return 2
    env["PG_BIN"] = str(tools / "bin")
    bootstrap = command("runtime-bootstrap", ["bash", "mvnw", *maven_options,
                                                "clean", "compile", "jar:jar", "install:install"], root, 240)
    if bootstrap["exit"] != 0:
        annotation("Note identity prerequisite failure", {"stage": "runtime-bootstrap", "exit": bootstrap["exit"], "rawLogSha256": bootstrap["rawLogSha256"]})
        return 2
    artifact = root / "target/server-facility-0.2.0-SNAPSHOT.jar"
    results["runtimeJarSha256"] = sha(artifact.read_bytes())
    for case in CASES:
        name = case["id"]
        application = private / name / "assembly api-示例-שלום"
        for step, args in [
            ("instantiate", [java, "templates/Instantiate.java", template.as_uri(), application.as_uri()]),
            ("overlay", [java, "examples/assembly-workflow/Apply.java", (root / "examples/assembly-workflow").as_uri(), application.as_uri()]),
            ("lineage", [java, "verification/template-consumer/TemplateLineage.java", application.as_uri()]),
        ]:
            operation = command(name + "-" + step, args, root, 45)
            if operation["exit"] != 0:
                annotation("Note identity fixture failure", {"case": name, "step": step, "exit": operation["exit"], "rawLogSha256": operation["rawLogSha256"]})
                return 2
        assert (application / TEST_REL).read_bytes() == (template / TEST_REL).read_bytes()
        assert (application / HELPER_REL).read_bytes() == (template / HELPER_REL).read_bytes()
        helper_before = sha((application / HELPER_REL).read_bytes())
        if case["trace"]:
            original = (application / HELPER_REL).read_text(encoding="utf-8")
            (application / HELPER_REL).write_text(control_helper(original, case["trace"]), encoding="utf-8", newline="\n")
        permitted = allowed_methods(application, case["classes"])
        args = ["bash", "mvnw", *maven_options,
                "-Dtest=" + ",".join(case["classes"]), "-DforkCount=1", "-DreuseForks=true",
                "-Dsurefire.runOrder=alphabetical", "-Djunit.jupiter.execution.parallel.enabled=false", "clean", "test"]
        outcome = command(name + "-test", args, application, case["seconds"])
        details = summarize_reports(application, permitted)
        item = {"id": name, "testBytesSha256": sha((application / TEST_REL).read_bytes()),
                "helperBeforeBytesSha256": helper_before, "helperAfterBytesSha256": sha((application / HELPER_REL).read_bytes()),
                "testLfSha256": lf_sha(application / TEST_REL), "exit": outcome["exit"], "timeout": outcome["timeout"],
                "expectedTests": sum(SUITE_EXPECTED[entry] for entry in case["classes"]), **details}
        item["compilerError"] = int("COMPILATION ERROR" in (private / (name + "-test.log")).read_text(encoding="utf-8", errors="replace"))
        results["cases"].append(item)
        save(public / "results.json", results)
        annotation("Note identity " + name, item)
        # A red test is data; execute the rest of this fixed matrix without retrying.
        # Timeout ends the campaign because cleanup and same-run resource ownership are unknown.
        if outcome["timeout"]:
            return 2
    results["harnessComplete"] = 1
    save(public / "results.json", results)
    # Preserve actual failure at job level, even when a deliberately constrained control is red.
    return 1 if any(x["exit"] != 0 or x["tests"] != x["expectedTests"] or x["failures"] or x["errors"]
                   or x["skipped"] or x["unrecognizedCases"] or x["failedDatabaseDrops"]
                   or len(x["forkCommandSha256"]) != 1
                   or len(x["targetMethods"]) != 2
                   or any(suite["tests"] != SUITE_EXPECTED[suite["suite"].removeprefix("com.example.api.")] for suite in x["suites"])
                   for x in results["cases"]) else 0


def self_test() -> None:
    import tempfile
    with tempfile.TemporaryDirectory(prefix="note-identity-sanitizer-") as temporary:
        app = Path(temporary)
        report = app / "target/surefire-reports"
        report.mkdir(parents=True)
        method = next(iter(METHODS))
        body = '<testsuite name="' + TARGET + '" tests="1" failures="1" errors="0" skipped="0"><testcase name="' + method + '"><failure type="java.lang.AssertionError" message="SECRET_JWT_HEADER_BODY">SECRET_CAUSE\n at ' + TARGET + '.' + method + '(NoteIdentityStorageTest.java:36)\n</failure></testcase></testsuite>'
        (report / ("TEST-" + TARGET + ".xml")).write_text(body, encoding="utf-8")
        result = summarize_reports(app, {TARGET: set(METHODS)})
        assert result["targetMethods"][0]["sourceLines"] == [36]
        assert "SECRET" not in json.dumps(result)
        assert result["targetMethods"][0]["assertionFailure"] == 1
        assert result["failures"] == 1
        # Source allowlist prevents arbitrary report testcase names from reaching output.
        (report / ("TEST-" + TARGET + ".xml")).write_text(body.replace(method, "SECRET_METHOD"), encoding="utf-8")
        result = summarize_reports(app, {TARGET: set(METHODS)})
        assert result["unrecognizedCases"] == 1 and "SECRET" not in json.dumps(result)
        prefix_suite = "com.example.api.DatabaseConfigurationTest"
        prefix = '<testsuite name="' + prefix_suite + '" tests="2" failures="0" errors="0" skipped="0"><testcase name="SECRET_PARAMETER(String)[1]"/><testcase name="SECRET_PARAMETER(String)[2]"/></testsuite>'
        (report / ("TEST-" + prefix_suite + ".xml")).write_text(prefix, encoding="utf-8")
        result = summarize_reports(app, {TARGET: set(METHODS), prefix_suite: set()})
        assert result["unrecognizedCases"] == 1 and "SECRET" not in json.dumps(result)
        assert result["tests"] == 3
    for case in CASES:
        if case["trace"]:
            assert re.fullmatch(r"[0-9a-f]{32}", case["trace"])
    assert "bad" not in CASES[2]["trace"] and "bad" in CASES[3]["trace"]
    print("SANITIZER_SELF_TEST_PASS controls=2 target_lines=1 untrusted_names=1 parameter_display_redaction=1")


if __name__ == "__main__":
    if sys.argv[1:] == ["self-test"]:
        self_test()
    elif sys.argv[1:] == ["run"]:
        try:
            sys.exit(execute(Path.cwd().resolve()))
        except Exception:
            # Never send a raw exception/cause into public job output.
            private = Path(".verification-results/note-identity/private")
            private.mkdir(parents=True, exist_ok=True)
            (private / "harness-exception.txt").write_text(traceback.format_exc(), encoding="utf-8")
            save(Path(".verification-results/note-identity/public/harness-error.json"), {"harnessError": 1})
            annotation("Note identity harness error", {"harnessError": 1})
            sys.exit(2)
    else:
        raise SystemExit("Use run or self-test")
