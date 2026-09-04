#!/usr/bin/env python3
"""Fail CI when a required Surefire contract suite did not fully execute."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def main() -> int:
    if len(sys.argv) < 3:
        print(
            "usage: assert_contract_reports.py REPORT_DIR ClassName:min-tests [...]",
            file=sys.stderr,
        )
        return 2

    report_dir = Path(sys.argv[1])
    failures: list[str] = []

    for requirement in sys.argv[2:]:
        class_name, separator, minimum_text = requirement.partition(":")
        if not separator:
            failures.append(f"invalid requirement {requirement!r}")
            continue

        report = report_dir / f"TEST-com.poseidon.codegraph.app.{class_name}.xml"
        if not report.is_file():
            failures.append(f"missing Surefire report: {report}")
            continue

        suite = ET.parse(report).getroot()
        counts = {
            name: int(suite.attrib.get(name, "0"))
            for name in ("tests", "skipped", "failures", "errors")
        }
        minimum = int(minimum_text)
        if counts["tests"] < minimum:
            failures.append(
                f"{class_name}: ran {counts['tests']} tests, expected at least {minimum}"
            )
        if counts["skipped"]:
            failures.append(f"{class_name}: {counts['skipped']} required tests were skipped")
        if counts["failures"] or counts["errors"]:
            failures.append(
                f"{class_name}: failures={counts['failures']} errors={counts['errors']}"
            )

        print(
            f"{class_name}: tests={counts['tests']} skipped={counts['skipped']} "
            f"failures={counts['failures']} errors={counts['errors']}"
        )

    if failures:
        print("\nContract execution gate failed:", file=sys.stderr)
        for failure in failures:
            print(f"- {failure}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
