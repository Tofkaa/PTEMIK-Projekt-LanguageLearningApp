"""Safety regressions for the gate itself; no Docker/database needed."""
import os
from pathlib import Path
import tempfile
import time
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from quality_gate import DisposablePostgres, child_environment, database_name, inventory, verify_reports


class GateSafetyTest(unittest.TestCase):
    def test_manifest_covers_every_guarded_spring_context(self):
        self.assertGreater(len(inventory()), 0)

    def test_deployment_and_opt_in_environment_is_not_inherited(self):
        with patch.dict(os.environ, {"UTC_TEST_DATABASE_URL": "unsafe", "JWT_SECRET": "secret",
                                     "SPRING_DATASOURCE_URL": "unsafe", "MAVEN_OPTS": "unsafe"}):
            env = child_environment()
        self.assertNotIn("UTC_TEST_DATABASE_URL", env)
        self.assertNotIn("JWT_SECRET", env)
        self.assertNotIn("MAVEN_OPTS", env)
        self.assertIn("127.0.0.1:1", env["SPRING_DATASOURCE_URL"])

    def test_app_database_and_sql_injection_names_are_rejected(self):
        for prefix, suffix in [("language_learning_db", ""), ("postgres", ""),
                               ("languageapp_smoke_check_", 'abc";DROP DATABASE postgres;'),
                               ("languageapp_smoke_check_", "f" * 64)]:
            with self.assertRaises(ValueError):
                database_name(prefix, suffix)
        self.assertEqual(database_name("languageapp_smoke_check_", "a" * 12),
                         "languageapp_smoke_check_" + "a" * 12)

    def check(self, xml, integration=False, guarded=None, required=None, stale=False):
        with tempfile.TemporaryDirectory() as folder:
            p = Path(folder) / "TEST-example.xml"
            if xml is not None:
                p.write_text(xml, encoding="utf-8")
                if stale:
                    os.utime(p, (1, 1))
            verify_reports(Path(folder), guarded or {"DatabaseTest": {}}, integration,
                           time.time(), required)

    def test_missing_empty_and_stale_reports_fail(self):
        for xml, stale in [(None, False), ("<testsuite/>", False),
                           ('<testsuite><testcase classname="DatabaseTest"/></testsuite>', True)]:
            with self.assertRaises(RuntimeError):
                self.check(xml, stale=stale)

    def test_skipped_integration_or_unit_test_fails(self):
        for integration, name in [(True, "DatabaseTest"), (False, "UnitTest")]:
            with self.assertRaises(RuntimeError):
                self.check(f'<testsuite><testcase classname="{name}"><skipped/></testcase></testsuite>',
                           integration=integration)

    def test_unit_gate_rejects_database_execution(self):
        with self.assertRaises(RuntimeError):
            self.check('<testsuite><testcase classname="DatabaseTest"/></testsuite>')

    def test_failures_and_missing_classes_fail(self):
        for xml in ['<testsuite errors="1"/>',
                    '<testsuite><testcase classname="DatabaseTest"><failure/></testcase></testsuite>',
                    '<testsuite><testcase classname="OtherTest"/></testsuite>']:
            with self.assertRaises(RuntimeError):
                self.check(xml, integration=True)

    def test_only_expected_database_skips_allowed_in_unit_mode(self):
        self.check('<testsuite><testcase classname="UnitTest"/>'
                   '<testcase classname="DatabaseTest"><skipped/></testcase></testsuite>')

    def test_full_integration_requires_each_class_to_run(self):
        self.check('<testsuite><testcase classname="DatabaseTest"/></testsuite>', integration=True)
        with self.assertRaises(RuntimeError):
            self.check('<testsuite><testcase classname="DatabaseTest"/></testsuite>',
                       integration=True, required={"DatabaseTest", "MissingUnitTest"})

    def test_failed_work_removes_only_its_own_container_and_volume(self):
        identifier = "a" * 64
        def docker(command, **kwargs):
            if "build" in command:
                return SimpleNamespace(stdout="sha256:" + "c" * 64)
            return SimpleNamespace(stdout="127.0.0.1:45678\n" if "port" in command else identifier)
        with patch("quality_gate.run", side_effect=docker) as commands, \
                patch("quality_gate.subprocess.run", return_value=SimpleNamespace(returncode=0)):
            with self.assertRaisesRegex(RuntimeError, "simulated failure"):
                with DisposablePostgres():
                    raise RuntimeError("simulated failure")
        self.assertEqual(commands.call_args.args[0],
                         ["docker", "rm", "--force", "--volumes", identifier])
        creation = next(call.args[0] for call in commands.call_args_list if "create" in call.args[0])
        self.assertIn("127.0.0.1::5432", creation)
        self.assertNotIn("--mount", creation)
        self.assertNotIn("--volume", creation)

    def test_failed_start_still_cleans_created_container(self):
        identifier = "b" * 64
        def docker(command, **kwargs):
            if "build" in command:
                return SimpleNamespace(stdout="sha256:" + "c" * 64)
            if "start" in command:
                raise RuntimeError("start failed")
            return SimpleNamespace(stdout=identifier)
        with patch("quality_gate.run", side_effect=docker) as commands:
            with self.assertRaisesRegex(RuntimeError, "start failed"):
                with DisposablePostgres():
                    self.fail("Failed startup must not enter test body")
        self.assertEqual(commands.call_args.args[0][-1], identifier)
        self.assertIn("rm", commands.call_args.args[0])


if __name__ == "__main__":
    unittest.main()
