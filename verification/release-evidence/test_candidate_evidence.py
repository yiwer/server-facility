"""Public CLI controls: two exact same-run identities, never partial success."""
import copy
import json
import pathlib
import subprocess
import sys
import tempfile
import unittest

SCRIPT = pathlib.Path(__file__).with_name("CandidateEvidence.py")
SOURCE = "1" * 40


def sample(os_name):
    quality = {"tests": 7, "failures": 0, "errors": 0, "skipped": 0,
               "coverage": {k: {"covered": 90, "missed": 10} for k in ("LINE", "INSTRUCTION", "BRANCH")}}
    roles = {}
    for role, gav, path, kind in (
        ("runtime", "cn.code91:server-facility:0.2.0-SNAPSHOT", "artifacts/server-facility-0.2.0-SNAPSHOT.jar", "ordinary"),
        ("partner", "cn.code91.examples:partner-aggregation:1.0-SNAPSHOT", "partner/partner-aggregation.jar", "ordinary"),
        ("securedApi", "com.example:secured-api:1.0.0-SNAPSHOT", "template/artifacts/secured-api-1.0.0-SNAPSHOT.jar", "boot"),
        ("workflow", "com.example:secured-api:1.0.0-SNAPSHOT", "workflow/artifacts/workflow-application.jar", "boot")):
        roles[role] = {"gav": gav, "path": path, "kind": kind, "bytes": 123, "sha256": "2" * 64,
                       "outputTimestamp": "1980-02-01T00:00:00Z", "quality": copy.deepcopy(quality)}
        if role != "runtime":
            roles[role]["nestedRuntimeSha256" if kind == "boot" else "classpathRuntimeSha256"] = "2" * 64
    return {"schemaVersion": 1, "mode": "all", "result": "PASS", "platform": "PASS", "source": SOURCE,
            "sourceClean": True, "runId": "123", "runAttempt": "1", "os": os_name,
            "environment": {"java": "25.0.4", "vendor": "Eclipse Adoptium", "maven": "3.10.0", "python": "3.12.3", "arch": "amd64",
                            "osVersion": "1", "timezone": "UTC", "locale": "en_US", "runnerImage": "test", "runnerImageVersion": "1"},
            "historical": {"result": "PASS", "runtimeSha256": "2" * 64, "beforeTests": 78, "afterTests": 80,
                           "packagedStages": ["before-platform", "after-platform", "after-virtual"],
                           "phases": {phase: {"quality": dict(copy.deepcopy(quality), tests=count), "bytes": 500,
                                              "sha256": "5" * 64, "runtimeSha256": "2" * 64}
                                      for phase, count in (("before", 78), ("after", 80))}},
            "inventory": {"source": SOURCE, "runtimeSha256": "2" * 64, "packages": 29, "apiSha256": "6" * 64, "dependencySha256": "7" * 64},
            "artifacts": roles}


class CandidateCliTest(unittest.TestCase):
    def compare(self, edit=None, raw=None):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            for os_name in ("Linux", "Windows"):
                folder = root / os_name
                folder.mkdir()
                data = sample(os_name)
                if edit and os_name == "Windows":
                    edit(data)
                (folder / "candidate-identity.json").write_text(raw if raw and os_name == "Windows" else json.dumps(data), encoding="utf-8")
            return subprocess.run([sys.executable, str(SCRIPT), "compare", "--linux", str(root / "Linux"),
                                   "--windows", str(root / "Windows"), "--source", SOURCE, "--run-id", "123", "--attempt", "1"],
                                  capture_output=True, text=True, timeout=15)

    def test_exact_four_role_same_run_pair_passes(self):
        result = self.compare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("identity=PASS", result.stdout)

    def test_each_delivery_hash_mismatch_refuses(self):
        for role in sample("Windows")["artifacts"]:
            with self.subTest(role=role):
                self.assertNotEqual(0, self.compare(lambda d: d["artifacts"][role].update(sha256="3" * 64)).returncode)

    def test_missing_malformed_nonpass_wrong_source_and_quality_refuse(self):
        changes = [lambda d: d.pop("artifacts"), lambda d: d.update(result="FAIL"),
                   lambda d: d.update(source="4" * 40), lambda d: d.update(runId="124"),
                   lambda d: d.update(runAttempt="2"), lambda d: d.update(sourceClean=False),
                   lambda d: d.update(platform="FAIL"), lambda d: d.update(os="Darwin"),
                   lambda d: d["artifacts"]["runtime"]["quality"].update(skipped=1),
                   lambda d: d["artifacts"]["runtime"]["quality"].update(tests=0),
                   lambda d: d["artifacts"]["runtime"]["quality"].update(tests=8),
                   lambda d: d["artifacts"]["runtime"]["quality"]["coverage"].pop("BRANCH"),
                   lambda d: d["artifacts"]["runtime"].update(bytes=True),
                   lambda d: d["artifacts"]["runtime"].update(path="../secret"),
                   lambda d: d["historical"].update(beforeTests=77),
                   lambda d: d["historical"].update(beforeTests=78.0),
                   lambda d: d["environment"].update(vendor="unsafe\n::notice::injection"),
                   lambda d: d["historical"]["phases"]["after"]["quality"].update(errors=1),
                   lambda d: d.update(inventory=None)]
        for index, change in enumerate(changes):
            with self.subTest(index=index):
                result = self.compare(change)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("REJECTED:", result.stderr)
        self.assertNotEqual(0, self.compare(raw='{"schemaVersion":1,"schemaVersion":1}').returncode)
        self.assertNotEqual(0, self.compare(raw='not json').returncode)

    def test_missing_os_file_and_extra_files_refuse(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            linux, windows = root / "Linux", root / "Windows"
            linux.mkdir()
            (linux / "candidate-identity.json").write_text(json.dumps(sample("Linux")), encoding="utf-8")
            command = [sys.executable, str(SCRIPT), "compare", "--linux", str(linux), "--windows", str(windows),
                       "--source", SOURCE, "--run-id", "123", "--attempt", "1"]
            for state in ("missing directory", "missing file", "extra file"):
                if state == "missing file":
                    windows.mkdir()
                if state == "extra file":
                    (windows / "candidate-identity.json").write_text(json.dumps(sample("Windows")), encoding="utf-8")
                    (windows / "stale.json").write_text("{}", encoding="utf-8")
                with self.subTest(state=state):
                    result = subprocess.run(command, capture_output=True, text=True, timeout=15)
                    self.assertNotEqual(0, result.returncode)
                    self.assertIn("REJECTED:", result.stderr)

    def test_qualify_requires_same_source_successful_platform_before_creating_file(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            candidate = sample("Windows")
            candidate.pop("platform")
            platform_gate = {k: v for k, v in candidate.items() if k not in ("artifacts", "historical")}
            platform_gate["mode"] = "platform"
            (root / "all.json").write_text(json.dumps(candidate), encoding="utf-8")
            command = [sys.executable, str(SCRIPT), "qualify", "--candidate", str(root / "all.json"), "--platform", str(root / "platform.json"),
                       "--source", SOURCE, "--run-id", "123", "--attempt", "1", "--os", "Windows"]
            for key, bad in (("result", "FAIL"), ("source", "4" * 40), ("runAttempt", "2")):
                (root / "platform.json").write_text(json.dumps(dict(platform_gate, **{key: bad})), encoding="utf-8")
                result = subprocess.run(command, capture_output=True, text=True, timeout=15)
                self.assertNotEqual(0, result.returncode)
                self.assertFalse((root / "candidate-identity.json").exists())
            (root / "platform.json").write_text(json.dumps(platform_gate), encoding="utf-8")
            result = subprocess.run(command, capture_output=True, text=True, timeout=15)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual("PASS", json.loads((root / "candidate-identity.json").read_text())["platform"])


    def test_qualify_rejects_python_equal_platform_types(self):
        for key, bad in (("schemaVersion", True), ("schemaVersion", 1.0), ("sourceClean", 1), ("sourceClean", 1.0)):
            with self.subTest(key=key, bad=bad), tempfile.TemporaryDirectory() as directory:
                root = pathlib.Path(directory)
                candidate = sample("Windows")
                candidate.pop("platform")
                platform_gate = {k: v for k, v in candidate.items() if k not in ("artifacts", "historical")}
                platform_gate.update(mode="platform", **{key: bad})
                (root / "all.json").write_text(json.dumps(candidate), encoding="utf-8")
                (root / "platform.json").write_text(json.dumps(platform_gate), encoding="utf-8")
                command = [sys.executable, str(SCRIPT), "qualify", "--candidate", str(root / "all.json"),
                           "--platform", str(root / "platform.json"), "--source", SOURCE,
                           "--run-id", "123", "--attempt", "1", "--os", "Windows"]
                result = subprocess.run(command, capture_output=True, text=True, timeout=15)
                self.assertNotEqual(0, result.returncode)
                self.assertFalse((root / "candidate-identity.json").exists())


if __name__ == "__main__":
    unittest.main()
