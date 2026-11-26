#!/usr/bin/env python3
"""
Виконання навантажувального тестування, тестування обсягом даних PostgreSQL (100 / 1 000 / 10 000 записів
із реальним EXPLAIN ANALYZE до та після індексів) та зняття реальних скріншотів через Playwright.
"""
from __future__ import annotations

import base64
import concurrent.futures
import json
import math
import platform
import subprocess
import time
import urllib.error
import urllib.request
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parent.parent
EVIDENCE_DIR = ROOT / "evidence"
SCREENSHOTS_DIR = ROOT / "docs" / "screenshots"
JACOCO_HTML = ROOT / "target" / "site" / "jacoco" / "index.html"

BASE_URL = "http://127.0.0.1:8096"
DEMO_PASS = "VoluntHub#2026!SafePassword"
VOL_EMAIL = "vol.ivanenko@volunthub.op.edu.ua"
COORD_EMAIL = "coord.odesa@volunthub.op.edu.ua"


def basic_header(email: str, password: str) -> dict[str, str]:
    token = base64.b64encode(f"{email}:{password}".encode("utf-8")).decode("ascii")
    return {"Authorization": f"Basic {token}"}


def percentile(sorted_vals: list[float], pct: float) -> float:
    if not sorted_vals:
        return 0.0
    idx = (len(sorted_vals) - 1) * (pct / 100.0)
    lo = math.floor(idx)
    hi = math.ceil(idx)
    if lo == hi:
        return round(sorted_vals[lo], 2)
    return round(sorted_vals[lo] + (sorted_vals[hi] - sorted_vals[lo]) * (idx - lo), 2)


def sample_actuator_telemetry() -> dict:
    info = {"ready": False, "jvm_mem_mb": 0.0, "cpu_pct": 0.0}
    try:
        with urllib.request.urlopen(f"{BASE_URL}/actuator/health/readiness", timeout=3) as r:
            info["ready"] = r.status == 200
        with urllib.request.urlopen(f"{BASE_URL}/actuator/metrics/jvm.memory.used", timeout=3) as r:
            data = json.loads(r.read().decode("utf-8"))
            info["jvm_mem_mb"] = round(data["measurements"][0]["value"] / (1024 * 1024), 1)
        with urllib.request.urlopen(f"{BASE_URL}/actuator/metrics/process.cpu.usage", timeout=3) as r:
            data = json.loads(r.read().decode("utf-8"))
            info["cpu_pct"] = round(data["measurements"][0]["value"] * 100.0, 2)
    except Exception:
        pass
    return info
