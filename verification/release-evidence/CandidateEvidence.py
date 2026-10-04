"""Small success-only evidence adapter for Verify and its two-platform CI gate."""
import argparse
import hashlib
import json
import os
import pathlib
import platform
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROLES = {
    "runtime": ("artifacts/server-facility-0.2.0-SNAPSHOT.jar", "cn.code91:server-facility:0.2.0-SNAPSHOT", "ordinary", "", "surefire-reports"),
    "partner": ("partner/partner-aggregation.jar", "cn.code91.examples:partner-aggregation:1.0-SNAPSHOT", "ordinary", "partner", "surefire-reports"),
    "securedApi": ("template/artifacts/secured-api-1.0.0-SNAPSHOT.jar", "com.example:secured-api:1.0.0-SNAPSHOT", "boot", "template", "surefire-reports"),
    "workflow": ("workflow/artifacts/workflow-application.jar", "com.example:secured-api:1.0.0-SNAPSHOT", "boot", "workflow", "positive-surefire-reports"),
}
TIMESTAMP = "1980-02-01T00:00:00Z"
COUNTERS = {"INSTRUCTION": 88, "LINE": 88, "BRANCH": 75}
STAGES = ["before-platform", "after-platform", "after-virtual"]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pairs(items):
    result = {}
    for key, value in items:
        require(key not in result, "duplicate JSON key")
        result[key] = value
    return result


def read(path):
    require(path.is_file() and 0 < path.stat().st_size <= 1024 * 1024, "missing or oversized manifest")
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=pairs)


def write(path, value):
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")
    temporary.replace(path)


def git(root, *args):
    return subprocess.check_output(["git", "-C", str(root), *args], timeout=30)


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def integer(value, positive=False):
    require(type(value) is int and (1 if positive else 0) <= value <= 9223372036854775807, "invalid integer")


def text(value):
    require(isinstance(value, str) and 0 < len(value) <= 160 and all(ord(c) >= 32 and ord(c) != 127 for c in value), "invalid environment text")


def quality(directory, reports="surefire-reports"):
    files = sorted((directory / reports).glob("TEST-*.xml"))
    require(files, "positive test reports missing")
    result = dict.fromkeys(("tests", "failures", "errors", "skipped"), 0)
    suites = set()
    for file in files:
        suite = ET.parse(file).getroot()
        require(suite.tag == "testsuite" and suite.attrib["name"] not in suites, "duplicate or invalid suite")
        suites.add(suite.attrib["name"])
        for key in result:
            count = int(suite.attrib[key])
            integer(count)
            result[key] += count
    coverage = ET.parse(directory / "jacoco/jacoco.xml").getroot()
    result["coverage"] = {}
    for item in coverage.findall("counter"):
        kind = item.attrib["type"]
        if kind in COUNTERS:
            require(kind not in result["coverage"], "duplicate coverage counter")
            result["coverage"][kind] = {key: int(item.attrib[key]) for key in ("covered", "missed")}
    validate_quality(result)
    return result


def validate_quality(value):
    require(type(value) is dict and set(value) == {"tests", "failures", "errors", "skipped", "coverage"}, "quality fields missing")
    integer(value["tests"], True)
    for key in ("failures", "errors", "skipped"):
        integer(value[key])
        require(value[key] == 0, "positive tests not successful")
    require(set(value["coverage"]) == set(COUNTERS), "coverage fields missing")
    for key, minimum in COUNTERS.items():
        counter = value["coverage"][key]
        require(set(counter) == {"covered", "missed"}, "invalid counter")
        for count in counter.values():
            integer(count)
        total = counter["covered"] + counter["missed"]
        require(total > 0 and counter["covered"] * 100 >= minimum * total, "coverage gate failed")


def model(path):
    root = ET.parse(path).getroot()
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    timestamp = root.findtext("m:properties/m:project.build.outputTimestamp", namespaces=namespace)
    require(timestamp == TIMESTAMP, "effective reproducible timestamp differs")
    gav = ":".join(root.findtext("m:" + part, namespaces=namespace) or "" for part in ("groupId", "artifactId", "version"))
    return gav, timestamp


def artifact(path, gav):
    with zipfile.ZipFile(path) as archive:
        require(len(archive.namelist()) == len(set(archive.namelist())), "duplicate ZIP entry")
        group, name, version = gav.split(":")
        candidates = [n for n in archive.namelist() if n.endswith(f"META-INF/maven/{group}/{name}/pom.properties")]
        require(len(candidates) == 1, "artifact Maven identity missing")
        properties = dict(line.split("=", 1) for line in archive.read(candidates[0]).decode("utf-8").splitlines() if "=" in line and not line.startswith("#"))
        require([properties.get(k) for k in ("groupId", "artifactId", "version")] == [group, name, version], "artifact coordinate differs")
    return {"bytes": path.stat().st_size, "sha256": sha(path)}


def start(args):
    source = git(args.root, "rev-parse", "HEAD").decode().strip()
    clean = not git(args.root, "status", "--porcelain").strip()
    # Git's clean status can normalize a stale CRLF checkout. Packaging uses raw bytes.
    packaging = ["pom.xml", "src/main", "templates/secured-api", "examples/partner-aggregation", "examples/assembly-workflow"]
    tracked = git(args.root, "ls-tree", "-r", "-z", "HEAD", "--", *packaging).decode().split("\0") if clean else []
    for entry in filter(None, tracked):
        metadata, name = entry.split("\t", 1)
        mode, kind, expected = metadata.split()
        require(kind == "blob" and mode in ("100644", "100755"), "unsupported packaging input")
        data = (args.root / name).read_bytes()
        actual = hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()
        require(actual == expected, "tracked packaging bytes differ: " + name)
    write(args.report / "source-start.json", {"source": source, "sourceClean": clean})


def finish(args):
    started = read(args.report / "source-start.json")
    require(started["source"] == git(args.root, "rev-parse", "HEAD").decode().strip(), "source changed during gate")
    clean = started["sourceClean"] and not git(args.root, "status", "--porcelain").strip()
    env = read(args.report / "environment.json")
    toolchain = list(args.report.glob("*-toolchain.log"))
    require(len(toolchain) == 1, "Maven version evidence missing")
    found = re.search(r"Apache Maven (\d+\.\d+\.\d+)", toolchain[0].read_text(encoding="utf-8", errors="replace"))
    require(found and found[1] == "3.10.0", "unexpected Maven version")
    env.update(maven=found[1], python=platform.python_version(), runnerImage=os.environ.get("ImageOS", "local"), runnerImageVersion=os.environ.get("ImageVersion", "local"))
    result = {"schemaVersion": 1, "mode": args.mode, "result": "PASS", "source": started["source"], "sourceClean": clean,
              "runId": os.environ.get("GITHUB_RUN_ID", "local"), "runAttempt": os.environ.get("GITHUB_RUN_ATTEMPT", "local"),
              "os": platform.system(), "environment": env}
    if os.environ.get("GITHUB_ACTIONS") == "true":
        require(clean and result["source"] == os.environ.get("GITHUB_SHA"), "CI source not exact and clean")
    if args.mode == "all":
        result["artifacts"] = {}
        runtime = args.report / ROLES["runtime"][0]
        require(runtime.read_bytes() == (args.root / "target" / runtime.name).read_bytes(), "archived runtime differs")
        runtime_hash = sha(runtime)
        for role, (relative, gav, kind, directory, reports) in ROLES.items():
            base = args.report / directory
            actual_gav, timestamp = model(base / "effective-pom.xml")
            require(actual_gav == gav, "effective coordinate differs")
            jar = args.report / relative
            info = {"gav": gav, "path": relative, "kind": kind, "outputTimestamp": timestamp,
                    "quality": quality(base, reports), **artifact(jar, gav)}
            if kind == "boot":
                with zipfile.ZipFile(jar) as archive:
                    nested = [n for n in archive.namelist() if n.startswith("BOOT-INF/lib/server-facility-")]
                    require(nested == ["BOOT-INF/lib/" + runtime.name], "wrong nested runtime")
                    require(archive.read(nested[0]) == runtime.read_bytes(), "nested runtime differs")
                info["nestedRuntimeSha256"] = runtime_hash
            elif role == "partner":
                classpath = (base / "runtime-classpath.txt").read_text(encoding="utf-8").strip().split(os.pathsep)
                bound = [pathlib.Path(p) for p in classpath if pathlib.Path(p).name.startswith("server-facility-")]
                require(len(bound) == 1 and bound[0].read_bytes() == runtime.read_bytes(), "partner runtime binding differs")
                info["classpathRuntimeSha256"] = runtime_hash
            result["artifacts"][role] = info
        result["historical"] = read(args.report / "historical-candidate/result.json")
        if clean:
            inventory = args.report / "release-inventory"
            subprocess.run([sys.executable, "-B", str(args.root / "verification/release-evidence/Inventory.py"),
                            "--repository", str(args.root), "--source", started["source"], "--jar", str(runtime),
                            "--jar-sha256", runtime_hash, "--all-evidence", str(args.report), "--output", str(inventory),
                            "--javap", str(args.javap), "--mode", "candidate"], check=True, timeout=90)
            packages = read(inventory / "package-api-inventory.json")["packageCount"]
            require(packages == 29, "candidate package inventory differs")
            result["inventory"] = {"source": started["source"], "runtimeSha256": runtime_hash, "packages": packages,
                                   "apiSha256": sha(inventory / "package-api-inventory.json"),
                                   "dependencySha256": sha(inventory / "dependency-inventory.json")}
        else:
            result["inventory"] = None
    target = args.report / ("all-identity.json" if args.mode == "all" else "platform-identity.json")
    write(target, result)
    output("manifest", target)


def output(name, path):
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as stream:
            stream.write(f"{name}={path.as_posix()}\n")
    print(f"{name}={path.as_posix()}")


def validate(value, os_name, source, run_id, attempt):
    require(value["schemaVersion"] == 1 and type(value["schemaVersion"]) is int and value["mode"] == "all" and value["result"] == "PASS" and value["platform"] == "PASS", "not qualified PASS")
    require(re.fullmatch("[0-9a-f]{40}", source) and value["source"] == source and value["sourceClean"] is True, "wrong or dirty source")
    require(re.fullmatch("[1-9][0-9]*", run_id) and re.fullmatch("[1-9][0-9]*", attempt), "invalid run identity")
    require(value["runId"] == run_id and value["runAttempt"] == attempt and value["os"] == os_name, "wrong run/attempt/OS")
    required_environment = {"java", "vendor", "maven", "python", "arch", "osVersion", "timezone", "locale", "runnerImage", "runnerImageVersion"}
    require(set(value["environment"]) == required_environment, "environment fields missing")
    for item in value["environment"].values():
        text(item)
    require(value["environment"]["java"].startswith("25.") and value["environment"]["maven"] == "3.10.0", "toolchain differs")
    python_version = value["environment"]["python"].split(".")
    require(len(python_version) == 3 and all(part.isdigit() for part in python_version) and tuple(map(int, python_version[:2])) >= (3, 11), "Python 3.11+ required")
    require(set(value["artifacts"]) == set(ROLES), "artifact roles missing")
    for role, (path, gav, kind, _, _) in ROLES.items():
        item = value["artifacts"][role]
        require(item["path"] == path and item["gav"] == gav and item["kind"] == kind, "artifact path/coordinate/role differs")
        require(isinstance(item["sha256"], str) and re.fullmatch("[0-9a-f]{64}", item["sha256"]), "invalid artifact hash")
        integer(item["bytes"], True)
        require(item["outputTimestamp"] == TIMESTAMP, "timestamp differs")
        validate_quality(item["quality"])
        if role != "runtime":
            require(item["nestedRuntimeSha256" if kind == "boot" else "classpathRuntimeSha256"] == value["artifacts"]["runtime"]["sha256"], "runtime binding differs")
    historical = value["historical"]
    integer(historical["beforeTests"], True)
    integer(historical["afterTests"], True)
    require(historical["result"] == "PASS" and historical["runtimeSha256"] == value["artifacts"]["runtime"]["sha256"]
            and historical["beforeTests"] == 78 and historical["afterTests"] == 80 and historical["packagedStages"] == STAGES, "historical qualification missing")
    require(set(historical["phases"]) == {"before", "after"}, "historical phase evidence missing")
    for phase, expected in (("before", 78), ("after", 80)):
        evidence = historical["phases"][phase]
        validate_quality(evidence["quality"])
        require(evidence["quality"]["tests"] == expected and evidence["runtimeSha256"] == historical["runtimeSha256"], "historical phase runtime/quality differs")
        integer(evidence["bytes"], True)
        require(isinstance(evidence["sha256"], str) and re.fullmatch("[0-9a-f]{64}", evidence["sha256"]), "historical artifact identity missing")
    inventory = value["inventory"]
    require(type(inventory) is dict and inventory["source"] == source and inventory["runtimeSha256"] == value["artifacts"]["runtime"]["sha256"], "candidate inventories not bound")
    integer(inventory["packages"], True)
    require(inventory["packages"] == 29, "package ledger incomplete")
    for key in ("apiSha256", "dependencySha256"):
        require(isinstance(inventory[key], str) and re.fullmatch("[0-9a-f]{64}", inventory[key]), "inventory hash missing")


def notice(value, identity=False):
    parts = ["identity=PASS" if identity else "all=PASS platform=PASS", "source=" + value["source"], "run=" + value["runId"], "attempt=" + value["runAttempt"], "os=" + value["os"]]
    parts.extend(key + "=" + item for key, item in sorted(value["environment"].items()))
    for role, item in value["artifacts"].items():
        parts.extend([role + "=" + item["sha256"], role + "Tests=" + str(item["quality"]["tests"])])
        for counter, counts in item["quality"]["coverage"].items():
            parts.append(role + counter + "=" + str(counts["covered"]) + "/" + str(counts["covered"] + counts["missed"]))
    message = " ".join(parts).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print("::notice title=Verified candidate::" + message)


def qualify(args):
    candidate, platform_gate = read(args.candidate), read(args.platform)
    require(type(platform_gate["schemaVersion"]) is int and platform_gate["schemaVersion"] == 1,
            "invalid platform schema version")
    require(platform_gate["sourceClean"] is True, "platform source is not clean")
    for key in ("schemaVersion", "source", "sourceClean", "runId", "runAttempt", "os", "environment"):
        require(candidate[key] == platform_gate[key], "platform identity differs")
    require(platform_gate["mode"] == "platform" and platform_gate["result"] == "PASS", "platform not PASS")
    candidate["platform"] = "PASS"
    validate(candidate, args.os, args.source, args.run_id, args.attempt)
    target = args.candidate.with_name("candidate-identity.json")
    write(target, candidate)
    output("manifest", target)
    notice(candidate)


def compare(args):
    manifests = []
    for directory, os_name in ((args.linux, "Linux"), (args.windows, "Windows")):
        require(directory.is_dir() and list(directory.iterdir()) == [directory / "candidate-identity.json"], "expected exactly one manifest per OS")
        value = read(directory / "candidate-identity.json")
        validate(value, os_name, args.source, args.run_id, args.attempt)
        manifests.append(value)
    for role in ROLES:
        for key in ("gav", "kind", "bytes", "sha256", "outputTimestamp"):
            require(manifests[0]["artifacts"][role][key] == manifests[1]["artifacts"][role][key], "cross-OS artifact mismatch: " + role + "/" + key)
        require(manifests[0]["artifacts"][role]["quality"]["tests"] == manifests[1]["artifacts"][role]["quality"]["tests"], "unexplained cross-OS test discovery difference: " + role)
    notice(manifests[0], identity=True)


def main():
    require(sys.version_info >= (3, 11), "Python 3.11+ required")
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest="operation", required=True)
    for operation in ("start", "finish"):
        command = commands.add_parser(operation)
        command.add_argument("--root", type=pathlib.Path, required=True)
        command.add_argument("--report", type=pathlib.Path, required=True)
        if operation == "finish":
            command.add_argument("--mode", choices=("all", "platform"), required=True)
            command.add_argument("--javap", type=pathlib.Path, required=True)
    for operation in ("compare", "qualify"):
        command = commands.add_parser(operation)
        for field in ("source", "run-id", "attempt"):
            command.add_argument("--" + field, required=True)
        if operation == "compare":
            for field in ("linux", "windows"):
                command.add_argument("--" + field, type=pathlib.Path, required=True)
        else:
            command.add_argument("--candidate", type=pathlib.Path, required=True)
            command.add_argument("--platform", type=pathlib.Path, required=True)
            command.add_argument("--os", choices=("Windows", "Linux"), required=True)
    args = parser.parse_args()
    try:
        globals()[args.operation](args)
    except (ValueError, KeyError, TypeError, OSError, ET.ParseError, zipfile.BadZipFile) as failure:
        print("REJECTED: " + str(failure), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
