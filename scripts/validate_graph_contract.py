#!/usr/bin/env python3
"""Compare a source-derived graph oracle with a persisted graph snapshot.

The expected JSON must be written from source semantics, never from parser output.
The actual JSON must be exported after the Engine has committed the graph.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


def load(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as stream:
        value = json.load(stream)
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return value


def node_key(node: dict[str, Any]) -> tuple[str, str]:
    return (str(node.get("kind", "")), str(node.get("id", "")))


def relationship_key(relationship: dict[str, Any]) -> tuple[str, str, str]:
    return (
        str(relationship.get("fromNodeId", "")),
        str(relationship.get("relationshipType", "")),
        str(relationship.get("toNodeId", "")),
    )


def endpoint_key(endpoint: dict[str, Any]) -> tuple[str, str]:
    return (str(endpoint.get("id", "")), str(endpoint.get("matchIdentity", "")))


def require_list(document: dict[str, Any], key: str) -> list[dict[str, Any]]:
    value = document.get(key, [])
    if not isinstance(value, list) or any(not isinstance(item, dict) for item in value):
        raise ValueError(f"{key} must be a list of objects")
    return value


def validate(layer: str, actual: dict[str, Any], expected: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    evidence = actual.get("evidence")
    if layer == "persisted":
        if not isinstance(evidence, dict) or evidence.get("kind") != "persisted-graph":
            errors.append("actual.evidence.kind must be persisted-graph for --layer persisted")
    elif layer == "parser":
        if isinstance(evidence, dict) and evidence.get("kind") == "persisted-graph":
            errors.append("parser layer cannot use persisted-graph evidence")

    actual_nodes = {node_key(item) for item in require_list(actual, "nodes")}
    expected_nodes = {node_key(item) for item in require_list(expected, "nodes")}
    missing_nodes = expected_nodes - actual_nodes
    if missing_nodes:
        errors.append(f"missing nodes: {sorted(missing_nodes)}")

    actual_relationships = {
        relationship_key(item) for item in require_list(actual, "relationships")
    }
    expected_relationships = {
        relationship_key(item) for item in require_list(expected, "relationships")
    }
    missing_relationships = expected_relationships - actual_relationships
    if missing_relationships:
        errors.append(f"missing relationships: {sorted(missing_relationships)}")

    forbidden_relationships = {
        relationship_key(item)
        for item in require_list(expected, "forbiddenRelationships")
    }
    forbidden_present = forbidden_relationships & actual_relationships
    if forbidden_present:
        errors.append(f"forbidden relationships present: {sorted(forbidden_present)}")

    policy = expected.get("relationshipPolicy", {})
    if isinstance(policy, dict) and policy.get("mode") == "exact":
        types = {str(value) for value in policy.get("types", [])}
        scoped_expected = {
            relation
            for relation in expected_relationships
            if relation[1] in types
            and ("", relation[0]) not in expected_nodes
            and ("", relation[2]) not in expected_nodes
        }
        scoped_actual = {
            relation
            for relation in actual_relationships
            if relation[1] in types
            and relation[0] in {node[1] for node in expected_nodes}
            and relation[2] in {node[1] for node in expected_nodes}
        }
        extra = scoped_actual - scoped_expected
        if extra:
            errors.append(f"unexpected relationships: {sorted(extra)}")

    if expected.get("requireNoDanglingRelationships"):
        node_ids = {node[1] for node in actual_nodes}
        dangling = {
            relation
            for relation in actual_relationships
            if relation[0] not in node_ids or relation[2] not in node_ids
        }
        if dangling:
            errors.append(f"dangling relationships: {sorted(dangling)}")

    expected_endpoints = {
        endpoint_key(item) for item in require_list(expected, "endpoints")
    }
    actual_endpoints = {
        endpoint_key(item) for item in require_list(actual, "endpoints")
    }
    missing_endpoints = expected_endpoints - actual_endpoints
    if missing_endpoints:
        errors.append(f"missing endpoints: {sorted(missing_endpoints)}")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--layer", choices=("persisted", "parser"), required=True)
    parser.add_argument("--actual", type=Path, required=True)
    parser.add_argument("--expected", type=Path, required=True)
    args = parser.parse_args()
    try:
        errors = validate(args.layer, load(args.actual), load(args.expected))
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"contract validation error: {error}", file=sys.stderr)
        return 2
    if errors:
        for error in errors:
            print(f"FAIL: {error}", file=sys.stderr)
        return 1
    print(f"PASS: {args.layer} graph contract")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
