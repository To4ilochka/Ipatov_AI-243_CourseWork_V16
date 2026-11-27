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
NEWBIE_EMAIL = "vol.newbie@volunthub.op.edu.ua"
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


def run_load_tests() -> dict:
    print("==> Прогрівання сервера перед навантажувальним тестуванням...")
    auth_hdr = basic_header(VOL_EMAIL, DEMO_PASS)
    for _ in range(30):
        req = urllib.request.Request(f"{BASE_URL}/api/public/tasks?city=%D0%9E%D0%B4%D0%B5%D1%81%D0%B0&onlyOpen=true")
        with urllib.request.urlopen(req, timeout=5) as r:
            r.read()

    vu_levels = [5, 10, 25, 50]
    iterations_per_vu = 24
    results = []
    telemetry_timeline = []

    for vu in vu_levels:
        print(f"==> Серія паралельного навантаження: {vu} віртуальних користувачів...")
        latencies_ms: list[float] = []
        errors = 0

        def worker(worker_idx: int) -> tuple[list[float], int]:
            local_lat = []
            local_err = 0
            for step in range(iterations_per_vu):
                if step % 2 == 0:
                    url = f"{BASE_URL}/api/public/tasks?page=0&size=6&onlyOpen=true"
                    headers = {}
                else:
                    url = f"{BASE_URL}/api/volunteer/recommendations?limit=6"
                    headers = auth_hdr
                t0 = time.perf_counter()
                try:
                    req = urllib.request.Request(url, headers=headers)
                    with urllib.request.urlopen(req, timeout=10) as resp:
                        resp.read()
                        if resp.status >= 500:
                            local_err += 1
                except Exception:
                    local_err += 1
                dt_ms = (time.perf_counter() - t0) * 1000.0
                local_lat.append(dt_ms)
            return local_lat, local_err

        t_start = time.perf_counter()
        with concurrent.futures.ThreadPoolExecutor(max_workers=vu) as ex:
            futures = [ex.submit(worker, i) for i in range(vu)]
            snap = sample_actuator_telemetry()
            for fut in concurrent.futures.as_completed(futures):
                lats, errs = fut.result()
                latencies_ms.extend(lats)
                errors += errs
        elapsed_s = time.perf_counter() - t_start
        snap_after = sample_actuator_telemetry()

        latencies_ms.sort()
        total_reqs = len(latencies_ms)
        entry = {
            "virtual_users": vu,
            "total_requests": total_reqs,
            "duration_sec": round(elapsed_s, 3),
            "rps": round(total_reqs / elapsed_s, 2),
            "mean_ms": round(sum(latencies_ms) / total_reqs, 2),
            "p50_ms": percentile(latencies_ms, 50),
            "p95_ms": percentile(latencies_ms, 95),
            "p99_ms": percentile(latencies_ms, 99),
            "max_ms": round(latencies_ms[-1], 2),
            "error_count": errors,
            "error_rate_pct": round(100.0 * errors / total_reqs, 2),
            "jvm_memory_mb": max(snap["jvm_mem_mb"], snap_after["jvm_mem_mb"]),
            "process_cpu_pct": max(snap["cpu_pct"], snap_after["cpu_pct"]),
        }
        telemetry_timeline.append(entry)
        results.append(entry)
        print("   ", entry)

    return {"runs": results, "telemetry": telemetry_timeline}


def psql_exec(db: str, sql: str) -> str:
    cmd = [
        "docker", "exec", "-i", "volunthub-postgres",
        "psql", "-U", "volunthub", "-d", db, "-Atc", sql
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", check=True)
    return proc.stdout.strip()


def run_volume_tests() -> dict:
    print("==> Підготовка ізольованої бази volunthub_volume у Docker PostgreSQL для об'ємного тестування...")
    psql_exec("postgres", "DROP DATABASE IF EXISTS volunthub_volume;")
    psql_exec("postgres", "CREATE DATABASE volunthub_volume;")

    migration_sql = (ROOT / "src" / "main" / "resources" / "db" / "migration" / "V1__init_schema.sql").read_text(encoding="utf-8")
    subprocess.run(
        ["docker", "exec", "-i", "volunthub-postgres", "psql", "-U", "volunthub", "-d", "volunthub_volume"],
        input=migration_sql, text=True, encoding="utf-8", check=True, capture_output=True
    )

    scales = [100, 1000, 10000]
    volume_rows = []

    target_query = """
        SELECT t.id, t.title, t.urgency, t.deadline_at, i.location_city
        FROM tasks t
        JOIN initiatives i ON i.id = t.initiative_id
        WHERE t.status = 'OPEN'
          AND i.status = 'ACTIVE'
          AND i.location_city = 'Одеса'
          AND t.urgency = 'CRITICAL'
        ORDER BY t.deadline_at ASC
        LIMIT 20;
    """

    for n in scales:
        psql_exec("volunthub_volume", "TRUNCATE execution_reports, applications, task_required_skills, tasks, initiatives, volunteer_skills, skills, users RESTART IDENTITY CASCADE;")
        seed_sql = f"""
        INSERT INTO skills (code, title, category) VALUES
            ('SK_1', 'Домедична просвіта', 'Безпека'),
            ('SK_2', 'Екологічний аудит', 'Екологія'),
            ('SK_3', 'Вебдоступність WCAG', 'ІТ'),
            ('SK_4', 'Логістика складу', 'Організація');

        INSERT INTO users (email, password_hash, full_name, role, city, phone)
        SELECT 'user' || g || '@volunthub.ua', 'argon2hash', 'Користувач ' || g,
               CASE WHEN g <= 10 THEN 'COORDINATOR' ELSE 'VOLUNTEER' END,
               (ARRAY['Одеса','Львів','Київ','Дніпро','Харків'])[1 + (g % 5)],
               '+380500000000'
        FROM generate_series(1, 220) AS g;

        INSERT INTO initiatives (coordinator_id, title, category, location_city, summary, status, starts_on, ends_on)
        SELECT 1 + (g % 10),
               'Ініціатива громади #' || g,
               (ARRAY['Екологія','Освіта та ІТ','Зоозахист','Безпека'])[1 + (g % 4)],
               (ARRAY['Одеса','Львів','Київ','Дніпро','Харків'])[1 + (g % 5)],
               'Синтетичний опис ініціативи для об''ємного експерименту.',
               CASE WHEN g % 10 = 0 THEN 'COMPLETED' ELSE 'ACTIVE' END,
               CURRENT_DATE - 10, CURRENT_DATE + 60
        FROM generate_series(1, GREATEST(20, {n} / 10)) AS g;

        INSERT INTO tasks (initiative_id, title, description, location_text, work_format, urgency, max_volunteers, approved_count, estimated_hours, deadline_at, status)
        SELECT 1 + (g % GREATEST(20, {n} / 10)),
               'Волонтерське завдання #' || g,
               'Докладний синтетичний опис робіт для перевірки планів виконання PostgreSQL при обсязі {n} записів.',
               'Навчальна локація #' || g,
               (ARRAY['ONSITE','REMOTE','HYBRID'])[1 + (g % 3)],
               (ARRAY['NORMAL','HIGH','CRITICAL'])[1 + (g % 3)],
               5, (g % 3), 4.0,
               NOW() + ((1 + (g % 45)) || ' days')::interval,
               CASE WHEN g % 8 = 0 THEN 'COMPLETED' ELSE 'OPEN' END
        FROM generate_series(1, {n}) AS g;

        INSERT INTO task_required_skills (task_id, skill_id, min_level, mandatory, weight)
        SELECT g, 1 + (g % 4), 2, (g % 2 = 0), 4
        FROM generate_series(1, {n}) AS g;

        INSERT INTO applications (task_id, volunteer_id, motivation, match_score, status)
        SELECT 1 + (g % {n}), 11 + (g % 200), 'Мотиваційний запис учасника #' || g, 82.5,
               (ARRAY['PENDING','APPROVED','REJECTED'])[1 + (g % 3)]
        FROM generate_series(1, {n}) AS g
        ON CONFLICT DO NOTHING;

        ANALYZE;
        """
        psql_exec("volunthub_volume", seed_sql)

        # Замір З ІНДЕКСАМИ (прогрітий буфер, медіана з 9 прогонів EXPLAIN ANALYZE)
        idx_times = []
        explain_with_idx = ""
        for run_i in range(9):
            out = psql_exec("volunthub_volume", "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + target_query)
            parsed = json.loads(out)[0]
            idx_times.append(parsed["Execution Time"])
            if run_i == 8:
                explain_with_idx = psql_exec("volunthub_volume", "EXPLAIN (ANALYZE, BUFFERS) " + target_query)

        # Тимчасово видаляємо пошукові індекси для чесного порівняння Seq Scan vs Index Scan
        psql_exec("volunthub_volume", """
            DROP INDEX IF EXISTS idx_tasks_status_urgency_deadline;
            DROP INDEX IF EXISTS idx_tasks_initiative_status;
            DROP INDEX IF EXISTS idx_initiatives_status_category_city;
            ANALYZE;
        """)
        seq_times = []
        explain_no_idx = ""
        for run_i in range(9):
            out = psql_exec("volunthub_volume", "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + target_query)
            parsed = json.loads(out)[0]
            seq_times.append(parsed["Execution Time"])
            if run_i == 8:
                explain_no_idx = psql_exec("volunthub_volume", "EXPLAIN (ANALYZE, BUFFERS) " + target_query)

        # Відновлюємо індекси для наступної ітерації
        psql_exec("volunthub_volume", """
            CREATE INDEX idx_initiatives_status_category_city ON initiatives (status, category, location_city);
            CREATE INDEX idx_tasks_initiative_status ON tasks (initiative_id, status);
            CREATE INDEX idx_tasks_status_urgency_deadline ON tasks (status, urgency, deadline_at ASC);
            ANALYZE;
        """)

        idx_times.sort()
        seq_times.sort()
        row = {
            "records_tasks": n,
            "median_no_index_ms": round(seq_times[len(seq_times) // 2], 3),
            "median_with_index_ms": round(idx_times[len(idx_times) // 2], 3),
            "speedup_factor": round(seq_times[len(seq_times) // 2] / max(0.01, idx_times[len(idx_times) // 2]), 2),
            "explain_no_index": explain_no_idx,
            "explain_with_index": explain_with_idx,
        }
        volume_rows.append(row)
        print(f"   Обсяг N={n}: без індексів={row['median_no_index_ms']} мс, з індексами={row['median_with_index_ms']} мс (прискорення x{row['speedup_factor']})")

    psql_exec("postgres", "DROP DATABASE IF EXISTS volunthub_volume;")
    return {"scales": volume_rows}


def capture_ui_screenshots():
    print("==> Зняття реальних скріншотів запущеного застосунку через Playwright...")
    SCREENSHOTS_DIR.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)

        # 1. Гість: публічний каталог завдань (десктоп 1280px) + порожній стан пошуку
        guest_ctx = browser.new_context(viewport={"width": 1280, "height": 880}, locale="uk-UA")
        page = guest_ctx.new_page()
        page.goto(f"{BASE_URL}/tasks", wait_until="networkidle")
        page.screenshot(path=str(SCREENSHOTS_DIR / "01_catalog_desktop.png"), full_page=False)

        page.goto(f"{BASE_URL}/tasks?query=%D0%9D%D0%B5%D1%96%D1%81%D0%BD%D1%83%D1%8E%D1%87%D0%B0%D0%86%D0%BD%D1%96%D1%86%D1%96%D0%B0%D1%82%D0%B8%D0%B2%D0%B0", wait_until="networkidle")
        page.screenshot(path=str(SCREENSHOTS_DIR / "02_empty_search.png"), full_page=False)
        guest_ctx.close()

        # 2. Волонтер: вхід, кабінет із персональними рекомендаціями за навичками, помилка валідації, успішна заявка + F5, спроба 403
        vol_ctx = browser.new_context(viewport={"width": 1280, "height": 920}, locale="uk-UA")
        vpage = vol_ctx.new_page()
        vpage.goto(f"{BASE_URL}/login", wait_until="networkidle")
        vpage.fill("#username", VOL_EMAIL)
        vpage.fill("#password", DEMO_PASS)
        vpage.click('button[type="submit"]')
        vpage.wait_for_url("**/volunteer/dashboard", wait_until="networkidle")
        vpage.screenshot(path=str(SCREENSHOTS_DIR / "03_volunteer_recommendations.png"), full_page=False)

        # Переходимо на перше рекомендоване відкрите завдання (напр. Менторські онлайн-консультації #3)
        vpage.goto(f"{BASE_URL}/tasks/3", wait_until="networkidle")
        vpage.fill("#motivation", "Закоротко")
        vpage.click('form[action="/volunteer/applications"] button[type="submit"]')
        vpage.wait_for_load_state("networkidle")
        vpage.screenshot(path=str(SCREENSHOTS_DIR / "04_task_apply_validation_error.png"), full_page=False)

        # Виправляємо мотивацію -> успішне подання заявки -> перевірка збереження після F5
        vpage.fill("#motivation", "Проводила підготовку школярів до олімпіад з інформатики, маю готові конспекти та вільні вечори вівторка й четверга.")
        vpage.click('form[action="/volunteer/applications"] button[type="submit"]')
        vpage.wait_for_url("**/volunteer/dashboard", wait_until="networkidle")
        vpage.reload(wait_until="networkidle")  # Перевірка збереження після F5!
        vpage.locator("#apps-heading").scroll_into_view_if_needed()
        vpage.screenshot(path=str(SCREENSHOTS_DIR / "05_application_saved_after_f5.png"), full_page=False)

        # Спроба недозволеної дії: звернення волонтера за прямим URL до панелі координатора -> HTTP 403
        vpage.goto(f"{BASE_URL}/coordinator/dashboard", wait_until="networkidle")
        vpage.screenshot(path=str(SCREENSHOTS_DIR / "06_access_denied_403.png"), full_page=False)
        vol_ctx.close()

        # 3. Координатор: панель керування заявками, авторизованими контактами та верифікацією звітів
        coord_ctx = browser.new_context(viewport={"width": 1280, "height": 940}, locale="uk-UA")
        cpage = coord_ctx.new_page()
        cpage.goto(f"{BASE_URL}/login", wait_until="networkidle")
        cpage.fill("#username", COORD_EMAIL)
        cpage.fill("#password", DEMO_PASS)
        cpage.click('button[type="submit"]')
        cpage.wait_for_url("**/coordinator/dashboard", wait_until="networkidle")
        cpage.screenshot(path=str(SCREENSHOTS_DIR / "07_coordinator_dashboard.png"), full_page=False)
        coord_ctx.close()

        # 4. Мобільна адаптивність на контрольній точці 360 CSS-пікселів
        mob_ctx = browser.new_context(viewport={"width": 360, "height": 800}, locale="uk-UA", is_mobile=True)
        mpage = mob_ctx.new_page()
        mpage.goto(f"{BASE_URL}/tasks", wait_until="networkidle")
        mpage.screenshot(path=str(SCREENSHOTS_DIR / "08_mobile_360px.png"), full_page=False)
        mob_ctx.close()

        # 5. Скріншот звіту покриття JaCoCo по пакетах проєкту
        if JACOCO_HTML.exists():
            jctx = browser.new_context(viewport={"width": 1180, "height": 620})
            jpage = jctx.new_page()
            jpage.goto(JACOCO_HTML.as_uri(), wait_until="networkidle")
            jpage.screenshot(path=str(SCREENSHOTS_DIR / "09_jacoco_coverage.png"), full_page=True)
            jctx.close()

        browser.close()
    print("==> Усі 9 реальних скріншотів збережено у docs/screenshots/.")


def main():
    env_info = {
        "os": platform.platform(),
        "processor": platform.processor(),
        "python": platform.python_version(),
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
    }
    load_res = run_load_tests()
    vol_res = run_volume_tests()
    capture_ui_screenshots()

    EVIDENCE_DIR.mkdir(parents=True, exist_ok=True)
    summary = {
        "environment": env_info,
        "load_testing": load_res,
        "volume_testing": vol_res,
    }
    (EVIDENCE_DIR / "benchmark_results.json").write_text(
        json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print("==> Протокол вимірювань збережено у evidence/benchmark_results.json")


if __name__ == "__main__":
    main()
