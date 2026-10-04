"""Archive and check the fixed historical recipe; Verify owns bounded execution."""
import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile
from CandidateEvidence import STAGES, artifact, git, quality, read, require, sha, write


def source_copy(source, target):
    shutil.copytree(source, target, ignore=shutil.ignore_patterns(".git", "target"))


def inputs(args):
    source = git(args.root, "rev-parse", "HEAD").decode().strip()
    require((args.root / "pom.xml").read_bytes() == git(args.root, "show", "HEAD:pom.xml"), "runtime POM changed")
    gav = "cn.code91:server-facility:0.2.0-SNAPSHOT"
    artifact(args.jar, gav)
    installed = args.repository / "cn/code91/server-facility/0.2.0-SNAPSHOT"
    require((installed / args.jar.name).read_bytes() == args.jar.read_bytes(), "installed candidate differs")
    pom = git(args.root, "show", "HEAD:pom.xml")
    require((installed / "server-facility-0.2.0-SNAPSHOT.pom").read_bytes() == pom, "installed candidate POM differs")
    (args.evidence / "runtime-pom.xml").write_bytes(pom)
    write(args.evidence / "runtime-manifest.json", {"sourceCommit": source,
          "sourceTree": git(args.root, "rev-parse", "HEAD:src").decode().strip(),
          "pomBlob": git(args.root, "rev-parse", "HEAD:pom.xml").decode().strip(), "coordinate": gav, "sha256": sha(args.jar)})
    helper = args.root / "verification/template-upgrade"
    for name in ("HistoricalUpgrade.py", "HistoricalPackagedUpgrade.java", "test-host-overlay.patch", "fixture-provenance.json"):
        shutil.copyfile(helper / name, args.evidence / name)
    provenance = read(helper / "fixture-provenance.json")
    require(sha(helper / "test-host-overlay.patch") == provenance["testHostOverlay"]["sha256"], "host overlay differs")
    fixture = args.fixture / "dev"
    fixture.mkdir(parents=True)
    for name, expected in provenance["externalFixtures"]["files"].items():
        data = git(args.root, "show", provenance["externalFixtures"]["source"] + ":templates/secured-api/dev/" + name)
        require(hashlib.sha256(data).hexdigest() == expected, "external fixture differs")
        (fixture / name).write_bytes(data)
    shutil.copytree(fixture, args.evidence / "external-fixture/dev")


def overlay(args):
    patch = args.evidence / "test-host-overlay.patch"
    git(args.app, "init")
    git(args.app, "config", "core.autocrlf", "false")
    git(args.app, "config", "core.eol", "lf")
    git(args.app, "apply", "--check", str(patch))
    git(args.app, "apply", str(patch))
    for name, expected in read(args.evidence / "fixture-provenance.json")["testHostOverlay"]["files"].items():
        require(sha(args.app / name) == expected, "host overlay postimage differs")


def archive(args):
    phase = args.evidence / args.phase
    phase.mkdir()
    source_copy(args.app, phase / "inputs")
    for source, destination in (("surefire-reports", "surefire-reports"), ("site/jacoco", "jacoco")):
        path = args.app / "target" / source
        if path.exists():
            shutil.copytree(path, phase / destination)
    for path in (args.app / "target").glob("*.jsonl"):
        shutil.copyfile(path, phase / path.name)
    for path in (args.app / "target").glob("*.log"):
        shutil.copyfile(path, phase / path.name)
    jar = args.app / "target/secured-api-1.0.0-SNAPSHOT.jar"
    if jar.exists():
        shutil.copyfile(jar, phase / "application.jar")


def check_phase(args):
    phase = args.evidence / args.phase
    actual = quality(phase)
    expected = 78 if args.phase == "before" else 80
    require(actual["tests"] == expected, "historical test discovery differs")
    custom = ET.parse(phase / "surefire-reports/TEST-com.example.api.CustomerUpgradeTest.xml").getroot()
    require(custom.attrib["tests"] == "4", "historical custom tests missing")
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    model = ET.parse(phase / "effective-pom.xml").getroot()
    dependencies = model.findall("m:dependencies/m:dependency", ns)
    runtime = [d for d in dependencies if d.findtext("m:groupId", namespaces=ns) == "cn.code91" and d.findtext("m:artifactId", namespaces=ns) == "server-facility"]
    require(len(runtime) == 1 and runtime[0].findtext("m:version", namespaces=ns) == "0.2.0-SNAPSHOT", "historical effective runtime differs")
    jar = phase / "application.jar"
    with zipfile.ZipFile(jar) as package:
        nested = [n for n in package.namelist() if n.startswith("BOOT-INF/lib/server-facility-")]
        require(nested == ["BOOT-INF/lib/" + args.jar.name] and package.read(nested[0]) == args.jar.read_bytes(), "historical nested runtime differs")
    result = {"quality": actual, "sha256": sha(jar), "bytes": jar.stat().st_size, "runtimeSha256": sha(args.jar)}
    write(phase / "phase-result.json", result)


def snapshot(app):
    return {p.relative_to(app).as_posix(): sha(p) for p in sorted(app.rglob("*")) if p.is_file() and ".git" not in p.relative_to(app).parts}


def archive_packaged(args):
    exported = args.evidence / "packaged"
    exported.mkdir(exist_ok=True)
    # Exact public proof only: fixture token/key/database state remains outside the archive.
    for name in [*(stage + suffix for stage in STAGES for suffix in (".log", "-result.txt")), "issuer.log", "database.log", "persisted-before.json", "note-location.txt"]:
        if (args.packaged / name).is_file():
            shutil.copyfile(args.packaged / name, exported / name)


def complete(args):
    before = snapshot(args.app)
    command = [sys.executable, str(args.root / "verification/template-upgrade/HistoricalUpgrade.py"), "upgrade", "--repo", str(args.root),
               "--app", str(args.app), "--runtime-manifest", str(args.evidence / "runtime-manifest.json"), "--runtime-jar", str(args.jar)]
    refusal = subprocess.run(command, capture_output=True, timeout=45)
    (args.evidence / "reapply-refusal.log").write_bytes(refusal.stdout + refusal.stderr)
    require(refusal.returncode != 0 and b"wrong source identity or already upgraded" in refusal.stderr, "historical reapply not refused")
    require(before == snapshot(args.app), "historical reapply changed sample")
    write(args.evidence / "reapply-result.json", {"result": "REFUSED_UNCHANGED", "manifest": before})
    packaged = args.packaged
    log = args.packaged_log.read_text(encoding="utf-8", errors="replace")
    require("HISTORICAL_PACKAGED_UPGRADE_PASS" in log and "DATABASE_STOP exit=0 postmasterAbsent=true" in log, "historical packaged lifecycle incomplete")
    for stage in STAGES:
        require((packaged / (stage + "-result.txt")).read_text(encoding="utf-8").startswith("PASS "), "historical packaged stage failed")
    archive_packaged(args)
    shutil.copyfile(args.packaged_log, args.evidence / "packaged/lifecycle.log")
    phases = {phase: read(args.evidence / phase / "phase-result.json") for phase in ("before", "after")}
    write(args.evidence / "result.json", {"result": "PASS", "runtimeSha256": sha(args.jar), "beforeTests": phases["before"]["quality"]["tests"],
          "afterTests": phases["after"]["quality"]["tests"], "packagedStages": STAGES, "phases": phases})


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("operation", choices=("inputs", "overlay", "archive", "check_phase", "archive_packaged", "complete"))
    for name in ("root", "app", "jar", "evidence", "repository", "fixture", "packaged", "packaged-log"):
        parser.add_argument("--" + name, type=pathlib.Path)
    parser.add_argument("--phase", choices=("before", "after"))
    args = parser.parse_args()
    globals()[args.operation](args)


if __name__ == "__main__":
    main()
