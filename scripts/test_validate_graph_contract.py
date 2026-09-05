#!/usr/bin/env python3
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
VALIDATOR = ROOT / "scripts" / "validate_graph_contract.py"


class GraphContractValidatorTest(unittest.TestCase):
    def setUp(self):
        self.expected = {
            "nodes": [
                {"kind": "unit", "id": "fixture::Service"},
                {"kind": "unit", "id": "fixture::Contract"},
                {"kind": "function", "id": "fixture::Service.save()"},
                {"kind": "function", "id": "fixture::Contract.save()"},
            ],
            "relationships": [
                {
                    "fromNodeId": "fixture::Service",
                    "relationshipType": "IMPLEMENTS",
                    "toNodeId": "fixture::Contract",
                },
                {
                    "fromNodeId": "fixture::Service.save()",
                    "relationshipType": "OVERRIDES",
                    "toNodeId": "fixture::Contract.save()",
                },
            ],
            "forbiddenRelationships": [
                {
                    "fromNodeId": "fixture::Service",
                    "relationshipType": "IMPLEMENTS",
                    "toNodeId": "fixture::WrongContract",
                }
            ],
            "relationshipPolicy": {
                "mode": "exact",
                "types": ["IMPLEMENTS", "OVERRIDES"],
            },
            "requireNoDanglingRelationships": True,
            "endpoints": [],
        }

    def run_validator(self, actual):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            expected_path = directory / "expected.json"
            actual_path = directory / "actual.json"
            expected_path.write_text(json.dumps(self.expected), encoding="utf-8")
            actual_path.write_text(json.dumps(actual), encoding="utf-8")
            return subprocess.run(
                [
                    sys.executable,
                    str(VALIDATOR),
                    "--layer",
                    "persisted",
                    "--actual",
                    str(actual_path),
                    "--expected",
                    str(expected_path),
                ],
                capture_output=True,
                text=True,
                check=False,
            )

    def persisted(self, relationships=None):
        return {
            "evidence": {"kind": "persisted-graph", "backend": "neo4j"},
            "nodes": self.expected["nodes"],
            "relationships": relationships or self.expected["relationships"],
            "endpoints": [],
        }

    def test_accepts_exact_persisted_graph(self):
        result = self.run_validator(self.persisted())
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_rejects_forbidden_edge(self):
        relationships = self.expected["relationships"] + [
            {
                "fromNodeId": "fixture::Service",
                "relationshipType": "IMPLEMENTS",
                "toNodeId": "fixture::WrongContract",
            }
        ]
        result = self.run_validator(self.persisted(relationships))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("forbidden relationships present", result.stderr)

    def test_rejects_dangling_edge(self):
        relationships = self.expected["relationships"] + [
            {
                "fromNodeId": "fixture::Service",
                "relationshipType": "CALLS",
                "toNodeId": "fixture::Missing",
            }
        ]
        result = self.run_validator(self.persisted(relationships))
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("dangling relationships", result.stderr)


if __name__ == "__main__":
    unittest.main()
