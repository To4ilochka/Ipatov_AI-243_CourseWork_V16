#!/usr/bin/env python3
"""Розрахунок 10 метрик програмного коду проєкту VoluntHub та збір показників покриття JaCoCo."""
from __future__ import annotations

import csv
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC_MAIN = ROOT / "src" / "main" / "java"
JACOCO_CSV = ROOT / "target" / "site" / "jacoco" / "jacoco.csv"
EVIDENCE_DIR = ROOT / "evidence"

METHOD_SIG_RE = re.compile(
    r"^\s*(?:public|protected|private)\s+(?:static\s+)?(?:final\s+)?(?:[\w<>\[\],\s\?]+)\s+(\w+)\s*\([^;]*\)\s*(?:throws\s+[\w,\s]+)?\{"
)
DECISION_RE = re.compile(r"\b(if|for|while|case|catch)\b|\?|\&\&|\|\|")
EXTENDS_RE = re.compile(r"\bclass\s+(\w+)(?:<[^>]+>)?\s+extends\s+([\w\.]+)")


def analyze_java_files():
    java_files = sorted(SRC_MAIN.rglob("*.java"))
    total_lines = 0
    code_lines = 0
    comment_lines = 0
    class_locs: list[tuple[str, int]] = []
    method_locs: list[tuple[str, int, int]] = []  # (qualified_name, loc, cc)
    parent_map: dict[str, str] = {}

    for path in java_files:
        lines = path.read_text(encoding="utf-8").splitlines()
        file_non_empty = 0
        in_block_comment = False

        for raw in lines:
            s = raw.strip()
            total_lines += 1
            if not s:
                continue
            file_non_empty += 1
            if in_block_comment:
                comment_lines += 1
                if "*/" in s:
                    in_block_comment = False
                continue
            if s.startswith("/*"):
                comment_lines += 1
                if "*/" not in s:
                    in_block_comment = True
                continue
            if s.startswith("//") or s.startswith("*"):
                comment_lines += 1
                continue
            if "//" in s:
                comment_lines += 1
            code_lines += 1

        class_name = path.stem
        class_locs.append((class_name, file_non_empty))

        full_text = "\n".join(lines)
        for m in EXTENDS_RE.finditer(full_text):
            parent_map[m.group(1)] = m.group(2).split(".")[-1]

        # Оцінка розміру та цикломатичної складності методів за балансом дужок
        i = 0
        while i < len(lines):
            m = METHOD_SIG_RE.match(lines[i])
            if not m:
                i += 1
                continue
            method_name = f"{class_name}.{m.group(1)}"
            brace_depth = lines[i].count("{") - lines[i].count("}")
            start_idx = i
            cc = 1 + len(DECISION_RE.findall(lines[i]))
            i += 1
            while i < len(lines) and brace_depth > 0:
                line_clean = re.sub(r'"(?:\\.|[^"\\])*"', '""', lines[i])
                line_clean = re.sub(r"//.*$", "", line_clean)
                brace_depth += line_clean.count("{") - line_clean.count("}")
                cc += len(DECISION_RE.findall(line_clean))
                i += 1
            m_loc = sum(1 for ln in lines[start_idx:i] if ln.strip())
            method_locs.append((method_name, m_loc, cc))

    def depth_of(cls: str, seen: set[str] | None = None) -> int:
        seen = seen or set()
        if cls in seen:
            return 1
        seen.add(cls)
        if cls not in parent_map:
            return 1  # java.lang.Object -> конкретний клас
        parent = parent_map[cls]
        if parent in {"OncePerRequestFilter", "RuntimeException"}:
            return 2
        return 1 + depth_of(parent, seen)

    all_classes = [c for c, _ in class_locs]
    max_dit = max((depth_of(c) for c in all_classes), default=1)

    jacoco_summary = {"line_covered": 0, "line_missed": 0, "branch_covered": 0, "branch_missed": 0, "packages": {}}
    if JACOCO_CSV.exists():
        with JACOCO_CSV.open("r", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            for row in reader:
                pkg = row["PACKAGE"]
                lm = int(row["LINE_MISSED"])
                lc = int(row["LINE_COVERED"])
                bm = int(row["BRANCH_MISSED"])
                bc = int(row["BRANCH_COVERED"])
                jacoco_summary["line_missed"] += lm
                jacoco_summary["line_covered"] += lc
                jacoco_summary["branch_missed"] += bm
                jacoco_summary["branch_covered"] += bc
                pkg_stat = jacoco_summary["packages"].setdefault(
                    pkg, {"classes": 0, "line_missed": 0, "line_covered": 0, "branch_missed": 0, "branch_covered": 0}
                )
                pkg_stat["classes"] += 1
                pkg_stat["line_missed"] += lm
                pkg_stat["line_covered"] += lc
                pkg_stat["branch_missed"] += bm
                pkg_stat["branch_covered"] += bc

    total_exec_lines = jacoco_summary["line_covered"] + jacoco_summary["line_missed"]
    line_cov_pct = round(100.0 * jacoco_summary["line_covered"] / total_exec_lines, 2) if total_exec_lines else 0.0
    total_branches = jacoco_summary["branch_covered"] + jacoco_summary["branch_missed"]
    branch_cov_pct = round(100.0 * jacoco_summary["branch_covered"] / total_branches, 2) if total_branches else 0.0

    max_class = max(class_locs, key=lambda x: x[1])
    max_method_loc = max(method_locs, key=lambda x: x[1])
    max_method_cc = max(method_locs, key=lambda x: x[2])

    non_empty_total = code_lines + comment_lines
    comment_pct = round(100.0 * comment_lines / non_empty_total, 2) if non_empty_total else 0.0

    report = {
        "files_count": len(java_files),
        "methods_count": len(method_locs),
        "total_physical_lines": total_lines,
        "total_non_empty_lines": non_empty_total,
        "executable_code_lines": code_lines,
        "comment_lines": comment_lines,
        "avg_class_loc": round(sum(x[1] for x in class_locs) / len(class_locs), 2),
        "max_class_loc": {"class": max_class[0], "loc": max_class[1]},
        "avg_method_loc": round(sum(x[1] for x in method_locs) / len(method_locs), 2),
        "max_method_loc": {"method": max_method_loc[0], "loc": max_method_loc[1]},
        "max_inheritance_depth": max_dit,
        "avg_cyclomatic_complexity": round(sum(x[2] for x in method_locs) / len(method_locs), 2),
        "max_cyclomatic_complexity": {"method": max_method_cc[0], "cc": max_method_cc[2]},
        "comment_ratio_percent": comment_pct,
        "jacoco_line_coverage_percent": line_cov_pct,
        "jacoco_branch_coverage_percent": branch_cov_pct,
        "jacoco": jacoco_summary,
    }

    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    out_path = EVIDENCE_DIR / "code_metrics.json"
    out_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    analyze_java_files()
