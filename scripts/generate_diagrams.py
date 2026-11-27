#!/usr/bin/env python3
"""
Програмна генерація повного комплекту інженерних діаграм (C4, UML Use Case з генералізацією акторів,
блок-схеми алгоритмів, карта станів UI, UML Deployment, шари компонентів, UML Sequence з alt,
ER-діаграма, UML Class домену й DTO, графіки навантажувального та об'ємного тестування)
у форматах SVG + PNG (240 DPI) зі строго ортогональною трасуванням без перетинів блоків і написів.
"""
from __future__ import annotations

import json
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch, Polygon, Ellipse, Rectangle

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "docs" / "diagrams"
BENCH_JSON = ROOT / "evidence" / "benchmark_results.json"

plt.rcParams["font.family"] = "DejaVu Sans"
plt.rcParams["svg.fonttype"] = "none"

C_INK = "#0F172A"
C_MUTED = "#475569"
C_PRIMARY = "#0F766E"
C_PRIMARY_FILL = "#F0FDFA"
C_BLUE = "#1D4ED8"
C_BLUE_FILL = "#EFF6FF"
C_AMBER = "#B45309"
C_AMBER_FILL = "#FFFBEB"
C_SLATE_FILL = "#F8FAFC"
C_BORDER = "#64748B"
C_DANGER = "#B91C1C"
C_DANGER_FILL = "#FEF2F2"


def new_canvas(w: float, h: float, xlim=(0, 100), ylim=(0, 100)):
    fig, ax = plt.subplots(figsize=(w, h), dpi=240)
    ax.set_xlim(*xlim)
    ax.set_ylim(*ylim)
    ax.axis("off")
    fig.patch.set_facecolor("white")
    return fig, ax


def save_both(fig, name: str):
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    fig.savefig(OUT_DIR / f"{name}.png", dpi=240, bbox_inches="tight", facecolor="white", pad_inches=0.14)
    fig.savefig(OUT_DIR / f"{name}.svg", bbox_inches="tight", facecolor="white", pad_inches=0.14)
    plt.close(fig)


def box(ax, x, y, w, h, text="", fc="white", ec=C_INK, lw=1.4, fontsize=9.2, bold=False, align="center", rounding=1.2):
    patch = FancyBboxPatch(
        (x, y), w, h,
        boxstyle=f"round,pad=0.2,rounding_size={rounding}",
        facecolor=fc, edgecolor=ec, linewidth=lw, zorder=3
    )
    ax.add_patch(patch)
    if text:
        tx = x + w / 2 if align == "center" else x + 1.4
        ha = "center" if align == "center" else "left"
        ax.text(tx, y + h / 2, text, ha=ha, va="center", fontsize=fontsize,
                fontweight="bold" if bold else "normal", color=C_INK, zorder=4, linespacing=1.26)
    return patch


def uml_class_box(ax, x, y, w, h, header: str, body_lines: list[str], stereotype: str | None = None,
                  fc="white", header_fc=C_BLUE_FILL, ec=C_INK, fontsize=8.3):
    outer = Rectangle((x, y), w, h, facecolor=fc, edgecolor=ec, linewidth=1.35, zorder=3)
    ax.add_patch(outer)
    hdr_h = 6.8 if stereotype else 5.0
    hdr = Rectangle((x, y + h - hdr_h), w, hdr_h, facecolor=header_fc, edgecolor=ec, linewidth=1.1, zorder=3.2)
    ax.add_patch(hdr)
    if stereotype:
        ax.text(x + w / 2, y + h - 2.1, stereotype, ha="center", va="center",
                fontsize=fontsize - 0.6, style="italic", color=C_MUTED, zorder=4)
        ax.text(x + w / 2, y + h - 4.8, header, ha="center", va="center",
                fontsize=fontsize + 0.4, fontweight="bold", color=C_INK, zorder=4)
    else:
        ax.text(x + w / 2, y + h - hdr_h / 2, header, ha="center", va="center",
                fontsize=fontsize + 0.4, fontweight="bold", color=C_INK, zorder=4)
    body_text = "\n".join(body_lines)
    ax.text(x + 1.1, y + h - hdr_h - 1.3, body_text, ha="left", va="top",
            fontsize=fontsize - 0.3, color=C_INK, zorder=4, linespacing=1.28)


def diamond(ax, cx, cy, w, h, text, fc=C_AMBER_FILL, ec=C_AMBER, fontsize=8.4):
    pts = [(cx, cy + h / 2), (cx + w / 2, cy), (cx, cy - h / 2), (cx - w / 2, cy)]
    poly = Polygon(pts, closed=True, facecolor=fc, edgecolor=ec, linewidth=1.4, zorder=3)
    ax.add_patch(poly)
    ax.text(cx, cy, text, ha="center", va="center", fontsize=fontsize, color=C_INK, zorder=4, linespacing=1.18)


def stick_actor(ax, cx, cy, label: str, fontsize=9.5):
    head = plt.Circle((cx, cy + 4.3), 1.45, facecolor="white", edgecolor=C_INK, linewidth=1.5, zorder=4)
    ax.add_patch(head)
    ax.plot([cx, cx], [cy + 2.85, cy - 0.6], color=C_INK, lw=1.5, zorder=4)
    ax.plot([cx - 2.1, cx + 2.1], [cy + 1.8, cy + 1.8], color=C_INK, lw=1.5, zorder=4)
    ax.plot([cx, cx - 1.8], [cy - 0.6, cy - 3.3], color=C_INK, lw=1.5, zorder=4)
    ax.plot([cx, cx + 1.8], [cy - 0.6, cy - 3.3], color=C_INK, lw=1.5, zorder=4)
    ax.text(cx, cy - 5.2, label, ha="center", va="top", fontsize=fontsize, fontweight="bold", color=C_INK, zorder=5)


def ortho_line(ax, points: list[tuple[float, float]], color=C_INK, lw=1.25, ls="-", zorder=2):
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    ax.plot(xs, ys, color=color, lw=lw, linestyle=ls, zorder=zorder, solid_capstyle="round")


def ortho_arrow(ax, points: list[tuple[float, float]], color=C_INK, lw=1.3, ls="-", label="", label_pos=None, fontsize=8.1):
    if len(points) > 2:
        ortho_line(ax, points[:-1], color=color, lw=lw, ls=ls, zorder=2)
    p1, p2 = points[-2], points[-1]
    ax.annotate(
        "", xy=p2, xytext=p1,
        arrowprops=dict(arrowstyle="-|>", color=color, lw=lw, linestyle=ls, mutation_scale=12, shrinkA=0, shrinkB=0),
        zorder=2.5
    )
    if label:
        lx, ly = label_pos if label_pos else ((p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2)
        ax.text(lx, ly, label, ha="center", va="center", fontsize=fontsize, color=C_INK, zorder=6,
                bbox=dict(boxstyle="round,pad=0.18", facecolor="white", edgecolor="none", alpha=0.98))


def hollow_generalization(ax, child_pt: tuple[float, float], parent_pt: tuple[float, float], via_pts=None):
    pts = [child_pt] + (via_pts or []) + [parent_pt]
    p_prev, p_end = pts[-2], pts[-1]
    dx = p_end[0] - p_prev[0]
    dy = p_end[1] - p_prev[1]
    length = max(0.001, (dx * dx + dy * dy) ** 0.5)
    ux, uy = dx / length, dy / length
    tri_len = 2.2
    tri_half_w = 1.25
    base_x = p_end[0] - ux * tri_len
    base_y = p_end[1] - uy * tri_len
    px, py = -uy, ux

    line_pts = pts[:-1] + [(base_x, base_y)]
    ortho_line(ax, line_pts, color=C_INK, lw=1.3, zorder=2.2)

    tri = Polygon(
        [p_end, (base_x + px * tri_half_w, base_y + py * tri_half_w), (base_x - px * tri_half_w, base_y - py * tri_half_w)],
        closed=True, facecolor="white", edgecolor=C_INK, linewidth=1.4, zorder=5
    )
    ax.add_patch(tri)


# 1. UML Use Case Diagram
def draw_use_case():
    fig, ax = new_canvas(11.2, 8.0, (0, 100), (0, 100))
    boundary = Rectangle((24, 8), 54, 86, facecolor="#FAFDFC", edgecolor=C_INK, linewidth=1.6, zorder=1)
    ax.add_patch(boundary)
    ax.text(51, 91.2, "Платформа волонтерських ініціатив VoluntHub", ha="center", va="center",
            fontsize=11, fontweight="bold", color=C_INK)

    stick_actor(ax, 10, 80, "Гість")
    stick_actor(ax, 10, 49, "Волонтер")
    stick_actor(ax, 90, 44, "Координатор")

    hollow_generalization(ax, (10, 55.2), (10, 72.5))
    hollow_generalization(ax, (90, 50.2), (10, 86.2), via_pts=[(90, 97.2), (10, 97.2)])

    def uc(cy, text, fc=C_SLATE_FILL, ec=C_PRIMARY):
        el = Ellipse((51, cy), 43, 8.2, facecolor=fc, edgecolor=ec, linewidth=1.35, zorder=3)
        ax.add_patch(el)
        ax.text(51, cy, text, ha="center", va="center", fontsize=8.7, color=C_INK, zorder=4)
        return (29.5, cy), (72.5, cy)

    u1_l, _ = uc(81, "UC-01 Переглянути та відфільтрувати\nкаталог завдань і пагінацію", C_BLUE_FILL, C_BLUE)
    u2_l, _ = uc(70, "UC-02 Зареєструватися або увійти\nдо облікового запису", C_BLUE_FILL, C_BLUE)
    u3_l, _ = uc(59, "UC-03 Отримати підбір завдань за\nнавичками з поясненням оцінки", C_PRIMARY_FILL, C_PRIMARY)
    u4_l, _ = uc(48, "UC-04 Оновити профіль навичок,\nподати або скасувати заявку", C_PRIMARY_FILL, C_PRIMARY)
    u5_l, _ = uc(37, "UC-05 Подати підсумковий звіт\nпро виконання завдання", C_PRIMARY_FILL, C_PRIMARY)
    _, u6_r = uc(26, "UC-06 Керувати власними ініціативами,\nзавданнями й лімітами місць", C_AMBER_FILL, C_AMBER)
    _, u7_r = uc(15, "UC-07 Погодити/відхилити заявки,\nпереглянути контакти й підтвердити звіт", C_AMBER_FILL, C_AMBER)

    ortho_line(ax, [(13.2, 81), u1_l], color=C_BLUE)
    ortho_line(ax, [(13.2, 78.5), (19.5, 78.5), (19.5, 70), u2_l], color=C_BLUE)

    ortho_line(ax, [(13.2, 51), (20.5, 51), (20.5, 59), u3_l], color=C_PRIMARY)
    ortho_line(ax, [(13.2, 48), u4_l], color=C_PRIMARY)
    ortho_line(ax, [(13.2, 45), (20.5, 45), (20.5, 37), u5_l], color=C_PRIMARY)

    ortho_line(ax, [(86.8, 43), (80.0, 43), (80.0, 26), u6_r], color=C_AMBER)
    ortho_line(ax, [(86.8, 40), (82.5, 40), (82.5, 15), u7_r], color=C_AMBER)

    ax.text(51, 3.5,
            "Примітка: лінії позначають асоціації ролей із прецедентами; порожнистий трикутник — UML-узагальнення ролі «Гість».",
            ha="center", va="center", fontsize=8.3, color=C_MUTED)
    save_both(fig, "01_use_case")


# 2. Flowchart of Skill-Based Recommendation Algorithm
def draw_algo_recommendation():
    fig, ax = new_canvas(10.6, 9.6, (0, 100), (0, 112))

    box(ax, 34, 104, 32, 5.5, "Початок", fc=C_SLATE_FILL, bold=True, rounding=2.6)
    box(ax, 23, 94.5, 54, 6.2,
        "Завантажити профіль волонтера v (місто, навички L_v)\nта активні заявки для виключення дублікатів", fc=C_BLUE_FILL, ec=C_BLUE)
    box(ax, 23, 85.0, 54, 6.2,
        "Вибрати відкриті завдання t активних ініціатив\n(approvedCount < maxVolunteers, deadlineAt > now)", fc=C_BLUE_FILL, ec=C_BLUE)

    diamond(ax, 50, 75.5, 38, 7.8, "Є наступне завдання t\nу списку кандидатів?")

    box(ax, 23, 62.5, 54, 6.4,
        "Обчислити зважене покриття навичок C_skill(v, t),\nмножник локації C_loc(v, t) та фактор терміновості C_urg(t)", fc=C_PRIMARY_FILL, ec=C_PRIMARY)

    diamond(ax, 50, 52.5, 40, 8.0, "Відсутня хоча б одна\nобов'язкова навичка?")

    box(ax, 6, 40.5, 36, 6.0, "Встановити штрафний множник\nP_mand = 0,35 та прапорець уваги", fc=C_DANGER_FILL, ec=C_DANGER, fontsize=8.5)
    box(ax, 58, 40.5, 36, 6.0, "Штраф не застосовується:\nмножник P_mand = 1,00", fc=C_PRIMARY_FILL, ec=C_PRIMARY, fontsize=8.5)

    box(ax, 21, 29.5, 58, 6.6,
        "Розрахувати S(v, t) = 100 · (α·C_skill + β·C_loc + γ·C_urg) · P_mand\nі сформувати текстове пояснення RecommendationExplanationDto", fc="white")

    box(ax, 23, 18.0, 54, 6.2,
        "Відсортувати результати за спаданням S(v, t), C_urg(t),\nзростанням deadlineAt та повернути топ-K рекомендацій", fc=C_BLUE_FILL, ec=C_BLUE)
    box(ax, 34, 8.5, 32, 5.5, "Кінець", fc=C_SLATE_FILL, bold=True, rounding=2.6)

    ortho_arrow(ax, [(50, 104), (50, 100.7)])
    ortho_arrow(ax, [(50, 94.5), (50, 91.2)])
    ortho_arrow(ax, [(50, 85.0), (50, 79.4)])
    ortho_arrow(ax, [(50, 71.6), (50, 68.9)], label="Так", label_pos=(53.2, 70.3))
    ortho_arrow(ax, [(50, 62.5), (50, 56.5)])

    ortho_arrow(ax, [(30, 52.5), (24, 52.5), (24, 46.5)], label="Так", label_pos=(27.0, 54.2))
    ortho_arrow(ax, [(70, 52.5), (76, 52.5), (76, 46.5)], label="Ні", label_pos=(73.0, 54.2))

    ortho_arrow(ax, [(24, 40.5), (24, 38.0), (42, 38.0), (42, 36.1)])
    ortho_arrow(ax, [(76, 40.5), (76, 38.0), (58, 38.0), (58, 36.1)])

    ortho_arrow(ax, [(21, 32.8), (3.2, 32.8), (3.2, 75.5), (31, 75.5)], color=C_PRIMARY)
    ortho_arrow(ax, [(69, 75.5), (96.8, 75.5), (96.8, 21.1), (77, 21.1)],
                label="Ні (усі оброблено)", label_pos=(83.0, 77.4))
    ortho_arrow(ax, [(50, 18.0), (50, 14.0)])

    save_both(fig, "02_algo_recommendation")


# 3. Flowchart of Atomic Seat Control & Application Submission
def draw_algo_atomic_application():
    fig, ax = new_canvas(11.0, 9.6, (0, 104), (0, 110))

    box(ax, 27, 102, 30, 5.2, "Початок", fc=C_SLATE_FILL, bold=True, rounding=2.5)
    box(ax, 14, 93, 56, 5.8,
        "Отримати запит (taskId, motivation) та перевірену сесію\nкористувача з контексту Spring Security", fc=C_BLUE_FILL, ec=C_BLUE)

    diamond(ax, 42, 83.2, 42, 7.6, "Сесія активна і роль\nROLE_VOLUNTEER?", fontsize=8.2)
    box(ax, 74, 80.5, 25, 5.4, "Відмова 401 / 403\nProblem Details", fc=C_DANGER_FILL, ec=C_DANGER, fontsize=8.4)

    diamond(ax, 42, 71.5, 42, 7.6, "Формат полів валідний\n(@Valid, 15..800 симв.)?", fontsize=8.2)
    box(ax, 74, 68.8, 25, 5.4, "Повернути 400\nі помилки полів", fc=C_DANGER_FILL, ec=C_DANGER, fontsize=8.4)

    box(ax, 14, 59.5, 56, 6.2,
        "Почати транзакцію (@Transactional READ_COMMITTED)\nі виконати SELECT ... FOR UPDATE рядка завдання", fc=C_PRIMARY_FILL, ec=C_PRIMARY)

    diamond(ax, 42, 48.5, 52, 9.6, "Ініціатива ACTIVE,\nзавдання OPEN, є місця\nі дедлайн дійсний?", fontsize=8.0)
    box(ax, 74, 45.8, 25, 5.4, "ROLLBACK;\nповернути 409 Conflict", fc=C_DANGER_FILL, ec=C_DANGER, fontsize=8.3)

    diamond(ax, 42, 35.0, 52, 9.6, "Частковий індекс активної\nзаявки (task_id, volunteer_id)\nне порушено?", fontsize=8.0)
    box(ax, 74, 32.3, 25, 5.4, "ROLLBACK;\n409 DUPLICATE", fc=C_DANGER_FILL, ec=C_DANGER, fontsize=8.3)

    box(ax, 14, 21.5, 56, 6.2,
        "Зберегти заявку (PENDING, matchScore), записати подію\nв audit_events, виконати COMMIT і повернути 201 Created", fc=C_PRIMARY_FILL, ec=C_PRIMARY)
    box(ax, 27, 11.0, 30, 5.2, "Кінець", fc=C_SLATE_FILL, bold=True, rounding=2.5)

    ortho_arrow(ax, [(42, 102), (42, 98.8)])
    ortho_arrow(ax, [(42, 93), (42, 87.0)])
    ortho_arrow(ax, [(63, 83.2), (74, 83.2)], label="Ні", label_pos=(68.5, 84.9))
    ortho_arrow(ax, [(42, 79.4), (42, 75.3)], label="Так", label_pos=(45.2, 77.3))

    ortho_arrow(ax, [(63, 71.5), (74, 71.5)], label="Ні", label_pos=(68.5, 73.2))
    ortho_arrow(ax, [(42, 67.7), (42, 65.7)], label="Так", label_pos=(45.2, 66.7))

    ortho_arrow(ax, [(42, 59.5), (42, 53.3)])
    ortho_arrow(ax, [(68, 48.5), (74, 48.5)], label="Ні", label_pos=(71.0, 50.2))
    ortho_arrow(ax, [(42, 43.7), (42, 39.8)], label="Так", label_pos=(45.2, 41.7))

    ortho_arrow(ax, [(68, 35.0), (74, 35.0)], label="Ні", label_pos=(71.0, 36.7))
    ortho_arrow(ax, [(42, 30.2), (42, 27.7)], label="Так", label_pos=(45.2, 28.9))
    ortho_arrow(ax, [(42, 21.5), (42, 16.2)])

    ortho_line(ax, [(99, 83.2), (101.8, 83.2), (101.8, 13.6)], color=C_DANGER)
    ortho_line(ax, [(99, 71.5), (101.8, 71.5)], color=C_DANGER)
    ortho_line(ax, [(99, 48.5), (101.8, 48.5)], color=C_DANGER)
    ortho_line(ax, [(99, 35.0), (101.8, 35.0)], color=C_DANGER)
    ortho_arrow(ax, [(101.8, 13.6), (57, 13.6)], color=C_DANGER)

    save_both(fig, "03_algo_atomic_application")


# 4. UI State Map
def draw_ui_state_map():
    fig, ax = new_canvas(11.6, 7.8, (0, 104), (0, 100))

    box(ax, 5, 83, 39, 11.5, "UI-01 Публічний каталог завдань\nпошук, фільтри, пагінація,\nвідображення порожньої вибірки", fc=C_BLUE_FILL, ec=C_BLUE, bold=True, fontsize=8.2)
    box(ax, 56, 83, 38, 11.5, "UI-02 Вхід / Реєстрація\nперевірка облікових даних,\nполітика паролів NIST ≥15 симв.", fc=C_SLATE_FILL, ec=C_BORDER, bold=True, fontsize=8.2)

    box(ax, 5, 61, 39, 12.0, "UI-03 Картка завдання і форма\nпояснення оцінки S(v,t),\nзалишок місць, подання заявки", fc=C_PRIMARY_FILL, ec=C_PRIMARY, bold=True, fontsize=8.2)
    box(ax, 56, 61, 38, 12.0, "UI-04 Стан помилки / конфлікту\nпоказ помилок полів (400/409),\nзбереження введеного тексту", fc=C_DANGER_FILL, ec=C_DANGER, bold=True, fontsize=8.2)

    box(ax, 5, 37, 39, 12.0, "UI-05 Кабінет волонтера\nнавички, рекомендації, мої заявки,\nзбереження стану після F5", fc=C_PRIMARY_FILL, ec=C_PRIMARY, bold=True, fontsize=8.2)
    box(ax, 56, 37, 38, 12.0, "UI-06 Панель координатора\nпогодження/відхилення заявок,\nавторизовані контакти учасників", fc=C_AMBER_FILL, ec=C_AMBER, bold=True, fontsize=8.2)

    box(ax, 5, 14, 39, 11.5, "UI-07 Підсумкові звіти\nвведення витрачених годин,\nперегляд статусу верифікації", fc=C_BLUE_FILL, ec=C_BLUE, bold=True, fontsize=8.2)
    box(ax, 56, 14, 38, 11.5, "UI-08 Сторінка відмови (403)\nблокування переходу без прав\nдо чужих панелей і записів", fc=C_DANGER_FILL, ec=C_DANGER, bold=True, fontsize=8.2)

    ortho_arrow(ax, [(24.5, 83), (24.5, 73)], label="обрати завдання", label_pos=(24.5, 78))
    ortho_arrow(ax, [(44, 69.5), (50, 69.5), (50, 89), (56, 89)], label="потрібен вхід", label_pos=(50, 91.5))
    ortho_arrow(ax, [(75, 83), (75, 77.5), (34, 77.5), (34, 73)], label="сесію створено", label_pos=(54.5, 79.3))

    ortho_arrow(ax, [(44, 66.5), (56, 66.5)], label="400 / 409", label_pos=(50, 68.3), color=C_DANGER)
    ortho_arrow(ax, [(56, 63.8), (44, 63.8)], label="виправити", label_pos=(50, 61.7), color=C_PRIMARY)

    ortho_arrow(ax, [(24.5, 61), (24.5, 49)], label="заявку створено (201)", label_pos=(24.5, 55))
    ortho_arrow(ax, [(24.5, 37), (24.5, 25.5)], label="заявку погоджено -> звіт", label_pos=(24.5, 31.2))

    # Обхід UI-04 по чистому правому зовнішньому коридору x=100.5!
    ortho_arrow(ax, [(94, 88.5), (100.5, 88.5), (100.5, 43), (94, 43)],
                label="роль COORDINATOR", label_pos=(95.0, 54.0), color=C_AMBER)

    ortho_arrow(ax, [(44, 44), (56, 44)], label="розгляд", label_pos=(50, 45.8), ls="--")
    ortho_arrow(ax, [(44, 40), (50, 40), (50, 19.8), (56, 19.8)], label="чужий URL -> 403", label_pos=(50, 29.0), color=C_DANGER)

    ax.text(50, 5.5,
            "Усі форми захищені CSRF-токеном і блокуванням повторного натискання кнопки під час відправлення запиту.",
            ha="center", va="center", fontsize=8.4, color=C_MUTED)
    save_both(fig, "04_ui_state_map")


# 5. C4 Container Diagram
def draw_c4_container():
    fig, ax = new_canvas(11.6, 7.8, (0, 100), (0, 100))

    box(ax, 5, 82, 26, 11.5, "Волонтер / Гість\n[Person]\nПошук завдань, навички,\nподання заявок і звітів", fc=C_BLUE_FILL, ec=C_BLUE, fontsize=8.3)
    box(ax, 69, 82, 26, 11.5, "Координатор ініціативи\n[Person]\nКерування завданнями,\nпогодження, верифікація", fc=C_AMBER_FILL, ec=C_AMBER, fontsize=8.3)

    box(ax, 28, 65, 44, 11,
        "Веббраузер користувача\n[Client Container: HTML5, CSS Grid, JS]\nАдаптивні форми, валідація полів, таблиці", fc=C_SLATE_FILL, ec=C_INK, fontsize=8.5)

    sys_box = Rectangle((8, 11), 84, 46, facecolor="#F8FFFE", edgecolor=C_PRIMARY, linewidth=1.6, linestyle="--", zorder=1)
    ax.add_patch(sys_box)
    # Заголовок межі у ПРАВОМУ верхньому куті (далеко від вертикальної стрілки x=29!)
    ax.text(89.5, 54.4, "Межа системи VoluntHub [System Boundary]", ha="right", va="center",
            fontsize=9.0, fontweight="bold", color=C_PRIMARY)

    box(ax, 12, 33, 34, 16,
        "Серверний вебзастосунок і REST API\n[Container: Java 21, Spring Boot 3.4]\nКонтролери MVC/REST, Spring Security\n(Argon2id), алгоритм S(v,t),\nполітика контактів, міграції Flyway", fc=C_PRIMARY_FILL, ec=C_PRIMARY, fontsize=8.1)

    box(ax, 60, 33, 28, 16,
        "Спостережуваність і телеметрія\n[Component: Actuator + MDC]\nПеревірки liveness / readiness,\nметрики JVM/CPU, requestId", fc=C_BLUE_FILL, ec=C_BLUE, fontsize=8.1)

    box(ax, 20, 14, 60, 11.5,
        "Реляційна база даних платформи\n[Container: PostgreSQL 16-alpine у Docker]\nТаблиці сутностей, частковий індекс заявок, блокування рядків і audit_events", fc="#FEFCE8", ec=C_AMBER, fontsize=8.2)

    ortho_arrow(ax, [(18, 82), (18, 70.5), (28, 70.5)], label="Дії у браузері", label_pos=(20.5, 72.6))
    ortho_arrow(ax, [(82, 82), (82, 70.5), (72, 70.5)], label="Керування", label_pos=(79.5, 72.6))

    ortho_arrow(ax, [(29, 65), (29, 49)], label="HTTP/HTTPS :8096\n(HTML + Problem JSON)", label_pos=(29, 59.8))
    ortho_arrow(ax, [(46, 41), (60, 41)], label="Метрики\nта логи", label_pos=(53, 41), ls="--")
    ortho_arrow(ax, [(29, 33), (29, 25.5)], label="JDBC / HikariCP :5446", label_pos=(29, 29.3))

    save_both(fig, "05_c4_container")


# 6. UML Deployment Diagram (заголовки вузлів ліворуч, вертикальний канал зв'язків праворуч x=74!)
def draw_deployment():
    fig, ax = new_canvas(11.2, 7.8, (0, 100), (0, 100))

    n_client = Rectangle((8, 73), 84, 22, facecolor=C_SLATE_FILL, edgecolor=C_INK, linewidth=1.5, zorder=1)
    ax.add_patch(n_client)
    ax.text(10.5, 92.2, "«device» Пристрій користувача (Desktop / Mobile)", ha="left", va="center", fontsize=9.3, fontweight="bold")
    box(ax, 14, 76, 72, 12.5,
        "«executionEnvironment» Веббраузер (Chromium 134+ / Firefox 135+)\nСемантичний інтерфейс Thymeleaf, CSS Grid (360 / 768 / 1280 px),\nсесійний cookie HttpOnly + SameSite=Lax", fc="white", fontsize=8.4)

    n_srv = Rectangle((8, 8), 84, 54, facecolor="#F8FAFC", edgecolor=C_INK, linewidth=1.5, zorder=1)
    ax.add_patch(n_srv)
    ax.text(10.5, 59.2, "«node» Навчальний сервер розгортання (Windows 11 / Linux)", ha="left", va="center", fontsize=9.3, fontweight="bold")

    box(ax, 14, 40, 72, 14.5,
        "«executionEnvironment» OpenJDK 21.0.12 LTS\nАртефакт: volunteer-platform-1.0.0.jar (Embedded Tomcat :8096)\nКонфігурація зі змінних середовища (.env.example)", fc=C_PRIMARY_FILL, ec=C_PRIMARY, fontsize=8.4)

    n_docker = Rectangle((12, 11), 76, 20.5, facecolor=C_BLUE_FILL, edgecolor=C_BLUE, linewidth=1.3, linestyle="--", zorder=2)
    ax.add_patch(n_docker)
    ax.text(14.5, 29.0, "«executionEnvironment» Docker Engine + Docker Compose", ha="left", va="center", fontsize=8.8, fontweight="bold", color=C_BLUE)
    box(ax, 16, 13, 68, 12.5,
        "Контейнер volunthub-postgres (postgres:16-alpine)\nВнутрішній порт 5432 (проброс 127.0.0.1:5446),\nпостійний том volunthub_pgdata", fc="white", ec=C_BLUE, fontsize=8.3)

    # Стрілки у вільному правому коридорі x=78, жоден заголовок ліворуч не перетинається!
    ortho_arrow(ax, [(78, 76), (78, 54.5)], label="HTTP / HTTPS :8096", label_pos=(78, 67.5))
    ortho_arrow(ax, [(78, 40), (78, 25.5)], label="TCP / JDBC :5446 -> :5432", label_pos=(78, 35.5))

    save_both(fig, "06_uml_deployment")


# 7. Design View: Logical Layers & Components (бічна колонка назв шарів ліворуч -> 0 перетинів заголовків!)
def draw_layers():
    fig, ax = new_canvas(11.8, 8.0, (0, 104), (0, 100))

    layers = [
        (76, 18, "1. Шар подання\n(Presentation\nLayer —\nweb, dto)", C_BLUE_FILL, C_BLUE),
        (52, 18, "2. Прикладний\nшар сценаріїв\n(Application —\nservice)", C_PRIMARY_FILL, C_PRIMARY),
        (28, 18, "3. Предметна\nмодель і правила\n(Domain —\ndomain, policy)", C_AMBER_FILL, C_AMBER),
        (4,  18, "4. Інфраструктура\nта безпека\n(repository,\nsecurity, config)", C_SLATE_FILL, C_BORDER),
    ]
    for y, h, title, fc, ec in layers:
        band = Rectangle((24, y), 76, h, facecolor=fc, edgecolor=ec, linewidth=1.3, zorder=1)
        ax.add_patch(band)
        hdr = Rectangle((4, y), 19, h, facecolor=fc, edgecolor=ec, linewidth=1.4, zorder=2)
        ax.add_patch(hdr)
        ax.text(13.5, y + h / 2, title, ha="center", va="center", fontsize=8.5, fontweight="bold", color=C_INK)

    # Layer 1 boxes
    box(ax, 26.5, 78.5, 22, 13, "PublicWebController\nVolunteerWebController\nCoordinatorWebController", fontsize=7.9)
    box(ax, 51.0, 78.5, 22, 13, "PlatformRestController\n(OpenAPI 3.1 /api/**)", fontsize=8.0)
    box(ax, 75.5, 78.5, 22, 13, "ApiExceptionAdvice\n(RFC 9457 Problem)\nApiDtos (@Valid)", fontsize=7.9)

    # Layer 2 boxes
    box(ax, 26.5, 54.5, 22, 13, "CatalogAndInitiative-\nService\n(пошук, пагінація)", fontsize=8.0)
    box(ax, 51.0, 54.5, 22, 13, "SkillMatchRecommen-\ndationService\n(індекс S(v,t))", fontsize=8.0)
    box(ax, 75.5, 54.5, 22, 13, "WorkflowCoordination-\nService\n(@Transactional)", fontsize=8.0)

    # Layer 3 boxes
    box(ax, 26.5, 30.5, 22, 13, "Initiative,\nVolunteerTask\n(інваріанти місць)", fontsize=8.0)
    box(ax, 51.0, 30.5, 22, 13, "TaskApplication,\nExecutionReport,\nSkill", fontsize=8.0)
    box(ax, 75.5, 30.5, 22, 13, "ContactVisibility-\nPolicy\n(захист контактів)", fontsize=8.0)

    # Layer 4 boxes
    box(ax, 26.5, 6.5, 22, 13, "JPA Repositories +\nTaskSpecifications\n(FOR UPDATE)", fontsize=7.9)
    box(ax, 51.0, 6.5, 22, 13, "SecurityConfig\n(Argon2id, CSRF,\nRBAC) + UserDetails", fontsize=7.9)
    box(ax, 75.5, 6.5, 22, 13, "Flyway V1 Schema +\nDemoDataSeeder +\nCorrelationFilter", fontsize=7.9)

    for cx in (37.5, 62.0, 86.5):
        ortho_arrow(ax, [(cx, 78.5), (cx, 67.5)])
        ortho_arrow(ax, [(cx, 54.5), (cx, 43.5)])
        ortho_arrow(ax, [(cx, 30.5), (cx, 19.5)])

    save_both(fig, "07_layers_components")


# 8. UML Sequence Diagram
def draw_sequence():
    fig, ax = new_canvas(11.4, 8.6, (0, 100), (0, 105))

    participants = [
        (12, "Браузер /\nКлієнт"),
        (36, "PlatformRest-\nController"),
        (63, "WorkflowCoordination-\nService"),
        (89, "PostgreSQL 16\n(Docker)"),
    ]
    for cx, title in participants:
        box(ax, cx - 9.5, 95, 19, 7.2, title, fc=C_BLUE_FILL, ec=C_BLUE, bold=True, fontsize=8.4)
        ax.plot([cx, cx], [95, 6], color=C_MUTED, lw=1.1, ls="--", zorder=1)

    ortho_arrow(ax, [(12, 89), (36, 89)], label="1: POST /api/volunteer/applications (@Valid)", label_pos=(24, 90.8))
    ortho_arrow(ax, [(36, 83), (63, 83)], label="2: submitApplication(volunteerId, req)", label_pos=(49.5, 84.8))
    ortho_arrow(ax, [(63, 77), (89, 77)], label="3: BEGIN; SELECT task FOR UPDATE", label_pos=(76, 78.8))
    ortho_arrow(ax, [(89, 72), (63, 72)], ls="--", label="4: поточний стан місць і статусу", label_pos=(76, 73.7), color=C_MUTED)

    alt_rect = Rectangle((6, 12), 89, 55, facecolor="#FAFAFA", edgecolor=C_INK, linewidth=1.3, zorder=1.2)
    ax.add_patch(alt_rect)
    tag = Polygon([(6, 67), (15, 67), (15, 64.2), (13.2, 62.8), (6, 62.8)], closed=True, facecolor=C_AMBER_FILL, edgecolor=C_INK, lw=1.1, zorder=2)
    ax.add_patch(tag)
    ax.text(10.2, 64.9, "alt", ha="center", va="center", fontsize=8.6, fontweight="bold")
    ax.text(18, 64.8, "[місце вільне, строк дійсний і активного дубліката немає]", ha="left", va="center", fontsize=8.4, fontweight="bold", color=C_PRIMARY)

    ortho_arrow(ax, [(63, 58), (89, 58)], label="5a: INSERT INTO applications, audit_events", label_pos=(76, 59.8), color=C_PRIMARY)
    ortho_arrow(ax, [(63, 52), (89, 52)], label="6a: COMMIT", label_pos=(76, 53.7), color=C_PRIMARY)
    ortho_arrow(ax, [(63, 46), (36, 46)], ls="--", label="7a: ApplicationDto (PENDING, matchScore)", label_pos=(49.5, 47.7), color=C_PRIMARY)
    ortho_arrow(ax, [(36, 41), (12, 41)], ls="--", label="8a: HTTP 201 Created + JSON", label_pos=(24, 42.7), color=C_PRIMARY)

    ax.plot([6, 95], [36, 36], color=C_INK, lw=1.1, ls="--", zorder=2)
    ax.text(18, 33.4, "[ліміт учасників вичерпано або активна заявка вже існує]", ha="left", va="center", fontsize=8.4, fontweight="bold", color=C_DANGER)

    ortho_arrow(ax, [(63, 27), (89, 27)], label="5b: ROLLBACK транзакції", label_pos=(76, 28.7), color=C_DANGER)
    ortho_arrow(ax, [(63, 21), (36, 21)], ls="--", label="6b: DomainConflictException", label_pos=(49.5, 22.7), color=C_DANGER)
    ortho_arrow(ax, [(36, 15.5), (12, 15.5)], ls="--", label="7b: HTTP 409 Conflict (ProblemDetail RFC 9457)", label_pos=(24, 17.2), color=C_DANGER)

    ax.text(50, 3.0, "Пунктирні стрілки позначають відповіді; власник заявки визначається виключно з перевіреної сесії сервера.",
            ha="center", va="center", fontsize=8.2, color=C_MUTED)
    save_both(fig, "08_uml_sequence_apply")


# 9. ER Diagram
def draw_er_diagram():
    fig, ax = new_canvas(12.2, 8.8, (0, 112), (0, 100))

    uml_class_box(ax, 5, 70, 28, 25, "users", [
        "PK id : BIGSERIAL",
        "UQ email : VARCHAR(120)",
        "password_hash : VARCHAR(255)",
        "full_name : VARCHAR(120)",
        "role : VARCHAR(24)",
        "city, phone, telegram_handle",
        "active : BOOLEAN, created_at"
    ], header_fc=C_BLUE_FILL)

    uml_class_box(ax, 42, 70, 28, 25, "initiatives", [
        "PK id : BIGSERIAL",
        "FK coordinator_id -> users.id",
        "title : VARCHAR(160)",
        "category : VARCHAR(60)",
        "location_city : VARCHAR(80)",
        "status : DRAFT|ACTIVE|...",
        "starts_on, ends_on, version"
    ], header_fc=C_AMBER_FILL)

    uml_class_box(ax, 79, 70, 28, 25, "tasks", [
        "PK id : BIGSERIAL",
        "FK initiative_id -> initiatives.id",
        "title, description, location_text",
        "work_format : ONSITE|REMOTE|...",
        "urgency : NORMAL|HIGH|CRITICAL",
        "max_volunteers, approved_count",
        "deadline_at, status, version"
    ], header_fc=C_PRIMARY_FILL)

    uml_class_box(ax, 5, 36, 28, 22, "volunteer_skills", [
        "PK id : BIGSERIAL",
        "FK volunteer_id -> users.id",
        "FK skill_id -> skills.id",
        "proficiency_level : SMALLINT(1..3)",
        "UQ (volunteer_id, skill_id)"
    ], header_fc=C_SLATE_FILL)

    uml_class_box(ax, 42, 36, 28, 22, "skills (довідник)", [
        "PK id : BIGSERIAL",
        "UQ code : VARCHAR(48)",
        "UQ title : VARCHAR(100)",
        "category : VARCHAR(60)"
    ], header_fc="#EDE9FE")

    uml_class_box(ax, 79, 36, 28, 22, "task_required_skills", [
        "PK id : BIGSERIAL",
        "FK task_id -> tasks.id",
        "FK skill_id -> skills.id",
        "min_level : SMALLINT(1..3)",
        "mandatory : BOOL, weight : 1..5"
    ], header_fc=C_SLATE_FILL)

    uml_class_box(ax, 20, 4, 32, 23, "applications", [
        "PK id : BIGSERIAL",
        "FK task_id -> tasks.id",
        "FK volunteer_id -> users.id",
        "FK decided_by -> users.id",
        "motivation, match_score(5,2)",
        "status : PENDING|APPROVED|...",
        "Partial UQ (task_id, volunteer_id)"
    ], header_fc=C_PRIMARY_FILL)

    uml_class_box(ax, 64, 4, 34, 23, "execution_reports", [
        "PK id : BIGSERIAL",
        "UQ FK application_id -> applications.id",
        "FK author_id -> users.id",
        "summary : VARCHAR(2000)",
        "hours_spent : NUMERIC(5,1)",
        "status : SUBMITTED|VERIFIED|...",
        "coordinator_feedback, verified_at"
    ], header_fc=C_BLUE_FILL)

    ortho_line(ax, [(33, 83), (42, 83)])
    ax.text(34.3, 84.6, "1", fontsize=8.2, fontweight="bold")
    ax.text(38.5, 84.6, "0..*", fontsize=8.2, fontweight="bold")

    ortho_line(ax, [(70, 83), (79, 83)])
    ax.text(71.3, 84.6, "1", fontsize=8.2, fontweight="bold")
    ax.text(75.5, 84.6, "0..*", fontsize=8.2, fontweight="bold")

    ortho_line(ax, [(19, 70), (19, 58)])
    ax.text(20.4, 67.6, "1", fontsize=8.2, fontweight="bold")
    ax.text(20.4, 59.6, "0..*", fontsize=8.2, fontweight="bold")

    ortho_line(ax, [(42, 47), (33, 47)])
    ax.text(39.8, 48.5, "1", fontsize=8.2, fontweight="bold")
    ax.text(34.2, 48.5, "0..*", fontsize=8.2, fontweight="bold")

    ortho_line(ax, [(70, 47), (79, 47)])
    ax.text(71.3, 48.5, "1", fontsize=8.2, fontweight="bold")
    ax.text(75.5, 48.5, "0..*", fontsize=8.2, fontweight="bold")

    ortho_line(ax, [(93, 70), (93, 58)])
    ax.text(94.4, 67.6, "1", fontsize=8.2, fontweight="bold")
    ax.text(94.4, 59.6, "0..*", fontsize=8.2, fontweight="bold")

    ortho_line(ax, [(5, 75), (2.0, 75), (2.0, 16), (20, 16)], color=C_PRIMARY)
    ax.text(3.2, 76.6, "1", fontsize=8.1, fontweight="bold")
    ax.text(16.2, 17.6, "0..*", fontsize=8.1, fontweight="bold")

    ortho_line(ax, [(107, 75), (109.8, 75), (109.8, 31), (36, 31), (36, 27)], color=C_PRIMARY)
    ax.text(107.6, 76.6, "1", fontsize=8.1, fontweight="bold")
    ax.text(37.4, 28.2, "0..*", fontsize=8.1, fontweight="bold")

    ortho_line(ax, [(52, 15.5), (64, 15.5)], color=C_BLUE)
    ax.text(53.5, 17.2, "1", fontsize=8.2, fontweight="bold")
    ax.text(60.0, 17.2, "0..1", fontsize=8.2, fontweight="bold")

    save_both(fig, "09_er_diagram")


# 10. UML Domain & Service Classes
def draw_uml_domain_classes():
    fig, ax = new_canvas(11.8, 8.2, (0, 108), (0, 100))

    uml_class_box(ax, 4, 68, 29, 26, "Initiative", [
        "- id: Long",
        "- coordinator: UserAccount",
        "- status: InitiativeStatus",
        "- startsOn, endsOn: LocalDate",
        "+ isActiveForApplications(): boolean"
    ], header_fc=C_AMBER_FILL)

    uml_class_box(ax, 39, 66, 32, 28, "VolunteerTask", [
        "- id: Long",
        "- workFormat: WorkFormat",
        "- urgency: TaskUrgency",
        "- maxVolunteers, approvedCount: int",
        "+ hasAvailableSeat(): boolean",
        "+ isAcceptingApplications(now): bool",
        "+ incrementApprovedCount(): void"
    ], header_fc=C_PRIMARY_FILL)

    uml_class_box(ax, 78, 68, 27, 26, "TaskApplication", [
        "- id: Long",
        "- matchScore: BigDecimal",
        "- status: ApplicationStatus",
        "+ approve(coord, note, now): void",
        "+ reject(coord, note, now): void",
        "+ cancelByVolunteer(now): void"
    ], header_fc=C_BLUE_FILL)

    uml_class_box(ax, 4, 34, 29, 24, "UserAccount", [
        "- id: Long",
        "- email: String",
        "- role: UserRole",
        "- city, phone, telegram: String",
        "- skills: List<VolunteerSkill>"
    ], header_fc=C_SLATE_FILL)

    uml_class_box(ax, 39, 34, 32, 24, "SkillMatchRecommendationService", [
        "- properties: RecommendationProperties",
        "+ evaluateTaskForVolunteer(v, t)",
        "+ recommendTasksForVolunteer(id, k)"
    ], stereotype="«service»", header_fc=C_PRIMARY_FILL)

    uml_class_box(ax, 78, 34, 27, 24, "ExecutionReport", [
        "- id: Long",
        "- hoursSpent: BigDecimal",
        "- status: ReportStatus",
        "+ updateByAuthor(...): void",
        "+ verify(feedback, now): void"
    ], header_fc=C_BLUE_FILL)

    uml_class_box(ax, 18, 5, 34, 21, "WorkflowCoordinationService", [
        "+ submitApplication(volId, req)",
        "+ approveApplication(coordId, appId)",
        "+ submitOrUpdateReport(volId, req)",
        "+ reviewReportByCoordinator(...)"
    ], stereotype="«service»", header_fc=C_PRIMARY_FILL)

    uml_class_box(ax, 60, 5, 34, 21, "ContactVisibilityPolicy", [
        "+ canViewVolunteerContacts(v, app)",
        "+ buildContactDto(viewer, app)",
        "+ requireAuthorizedContacts(v, app)"
    ], stereotype="«policy»", header_fc=C_AMBER_FILL)

    ortho_line(ax, [(33, 81), (39, 81)])
    ax.text(33.9, 82.5, "1", fontsize=8.1)
    ax.text(36.0, 82.5, "0..*", fontsize=8.1)

    ortho_line(ax, [(71, 81), (78, 81)])
    ax.text(72.0, 82.5, "1", fontsize=8.1)
    ax.text(74.4, 82.5, "0..*", fontsize=8.1)

    ortho_line(ax, [(91.5, 68), (91.5, 58)])
    ax.text(92.7, 65.8, "1", fontsize=8.1)
    ax.text(92.7, 59.5, "0..1", fontsize=8.1)

    ortho_arrow(ax, [(55, 58), (55, 66)], ls="--", label="оцінює", label_pos=(55, 62))
    ortho_arrow(ax, [(33, 46), (39, 46)], ls="--")
    ortho_arrow(ax, [(35, 26), (35, 30.5), (55, 30.5), (55, 34)], ls="--", label="викликає", label_pos=(45, 32.0))
    ortho_arrow(ax, [(52, 15.5), (60, 15.5)], ls="--", label="перевіряє", label_pos=(56, 17.5))

    save_both(fig, "10_uml_domain_classes")


# 11. Separate DTO View
def draw_uml_dto_classes():
    fig, ax = new_canvas(11.4, 7.2, (0, 105), (0, 100))

    uml_class_box(ax, 5, 56, 29, 34, "SubmitApplicationRequest", [
        "+ taskId: Long (@NotNull)",
        "+ motivation: String (15..800)",
        "---",
        "Примітка: volunteerId відсутній",
        "у запиті (береться із сесії)"
    ], stereotype="«DTO»", header_fc=C_BLUE_FILL)

    uml_class_box(ax, 38, 56, 30, 34, "ApplicationDto", [
        "+ id, taskId: Long",
        "+ taskTitle, initiativeTitle: String",
        "+ matchScore: BigDecimal",
        "+ status: ApplicationStatus",
        "+ contact: ParticipantContactDto"
    ], stereotype="«DTO»", header_fc=C_PRIMARY_FILL)

    uml_class_box(ax, 72, 56, 29, 34, "ParticipantContactDto", [
        "+ volunteerId: Long",
        "+ fullName, city: String",
        "+ contactsAuthorized: boolean",
        "+ email: String? (масковано)",
        "+ phone, telegramHandle: String?"
    ], stereotype="«DTO»", header_fc=C_AMBER_FILL)

    uml_class_box(ax, 5, 12, 31, 34, "RecommendedTaskDto", [
        "+ task: TaskCardDto",
        "+ explanation:",
        "    RecommendationExplanationDto"
    ], stereotype="«DTO»", header_fc=C_PRIMARY_FILL)

    uml_class_box(ax, 40, 12, 33, 34, "RecommendationExplanationDto", [
        "+ totalScore: double [0..100]",
        "+ skillComponentPoints: double",
        "+ locationComponentPoints: double",
        "+ missingMandatorySkill: boolean",
        "+ matchedSkills: List<String>",
        "+ summaryText: String"
    ], stereotype="«DTO»", header_fc=C_PRIMARY_FILL)

    uml_class_box(ax, 76, 12, 25, 34, "ProblemDetail (RFC 9457)", [
        "+ type: URI",
        "+ title: String",
        "+ status: int (400/403/409)",
        "+ detail, code: String",
        "+ fieldErrors: Map<String,String>",
        "+ requestId: String"
    ], stereotype="«DTO»", header_fc=C_DANGER_FILL, ec=C_DANGER)

    ortho_arrow(ax, [(68, 73), (72, 73)], label="1", label_pos=(70, 75))
    ortho_arrow(ax, [(36, 29), (40, 29)], label="1", label_pos=(38, 31))

    save_both(fig, "11_uml_dto_classes")


# 12 & 13. Real Benchmark Charts
def draw_benchmark_charts():
    if not BENCH_JSON.exists():
        return
    data = json.loads(BENCH_JSON.read_text(encoding="utf-8"))
    runs = data["load_testing"]["runs"]
    scales = data["volume_testing"]["scales"]

    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(11.8, 4.9), dpi=240)
    fig.patch.set_facecolor("white")

    vus = [str(r["virtual_users"]) for r in runs]
    x = list(range(len(vus)))
    w = 0.25

    ax1.bar([i - w for i in x], [r["p50_ms"] for r in runs], width=w, label="Медіана p50, мс", color="#0F766E")
    b2 = ax1.bar(x, [r["p95_ms"] for r in runs], width=w, label="Перцентиль p95, мс", color="#2563EB")
    ax1.bar([i + w for i in x], [r["p99_ms"] for r in runs], width=w, label="Перцентиль p99, мс", color="#D97706")

    ax1.set_xticks(x)
    ax1.set_xticklabels([f"{v} VU" for v in vus])
    ax1.set_ylabel("Час відповіді сервера, мс")
    ax1.set_xlabel("Кількість одночасних віртуальних користувачів")
    ax1.set_title("Затримки відповіді (p50 / p95 / p99)", pad=44, fontsize=10.5, fontweight="bold")
    ax1.grid(axis="y", linestyle="--", alpha=0.45)
    ax1.legend(loc="upper center", bbox_to_anchor=(0.5, 1.15), ncol=3, frameon=False, fontsize=8.3)
    ax1.bar_label(b2, fmt="%.1f", padding=2, fontsize=7.8)
    ax1.set_ylim(0, max(r["p99_ms"] for r in runs) * 1.15)

    rps_vals = [r["rps"] for r in runs]
    mem_vals = [r["jvm_memory_mb"] for r in runs]
    l1 = ax2.plot(x, rps_vals, marker="o", lw=2.2, color="#0F766E", label="Пропускна здатність, запитів/с")
    ax2.set_xticks(x)
    ax2.set_xticklabels([f"{v} VU" for v in vus])
    ax2.set_ylabel("Пропускна здатність, запитів/с", color="#0F766E")
    ax2.set_xlabel("Кількість одночасних віртуальних користувачів")
    ax2.grid(axis="y", linestyle="--", alpha=0.45)
    ax2.set_ylim(0, max(rps_vals) * 1.25)

    ax2_r = ax2.twinx()
    l2 = ax2_r.plot(x, mem_vals, marker="s", lw=1.9, ls="--", color="#7C3AED", label="Пам'ять JVM Heap, МБ")
    ax2_r.set_ylabel("Використання пам'яті JVM, МБ", color="#7C3AED")
    ax2_r.set_ylim(0, max(mem_vals + [100.0]) * 1.30)

    ax2.set_title("Пропускна здатність та телеметрія JVM", pad=44, fontsize=10.5, fontweight="bold")
    lines = l1 + l2
    labels = [ln.get_label() for ln in lines]
    ax2.legend(lines, labels, loc="upper center", bbox_to_anchor=(0.5, 1.15), ncol=2, frameon=False, fontsize=8.3)

    fig.tight_layout()
    save_both(fig, "12_load_test_chart")

    fig2, axv = plt.subplots(figsize=(8.6, 4.8), dpi=240)
    fig2.patch.set_facecolor("white")

    labels_v = [f"{s['records_tasks']:,}".replace(",", " ") + " зап." for s in scales]
    xv = list(range(len(labels_v)))
    wv = 0.34

    no_idx = [s["median_no_index_ms"] for s in scales]
    with_idx = [s["median_with_index_ms"] for s in scales]

    bv1 = axv.bar([i - wv / 2 for i in xv], no_idx, width=wv, label="Без складених індексів (Seq Scan), мс", color="#DC2626")
    bv2 = axv.bar([i + wv / 2 for i in xv], with_idx, width=wv, label="З B-Tree індексами V1 (Index Scan), мс", color="#0F766E")

    axv.set_xticks(xv)
    axv.set_xticklabels(labels_v)
    axv.set_ylabel("Медіанний час виконання SQL-запиту, мс")
    axv.set_xlabel("Обсяг таблиць tasks / applications у СКБД PostgreSQL 16")
    axv.set_title("Порівняння часу виконання пошукового запиту при зростанні обсягу БД", pad=44, fontsize=10.8, fontweight="bold")
    axv.grid(axis="y", linestyle="--", alpha=0.45)
    axv.legend(loc="upper center", bbox_to_anchor=(0.5, 1.13), ncol=2, frameon=False, fontsize=8.8)
    axv.bar_label(bv1, fmt="%.2f мс", padding=3, fontsize=8.2)
    axv.bar_label(bv2, fmt="%.2f мс", padding=3, fontsize=8.2)
    axv.set_ylim(0, max(no_idx) * 1.24)

    fig2.tight_layout()
    save_both(fig2, "13_volume_test_chart")


def main():
    draw_use_case()
    draw_algo_recommendation()
    draw_algo_atomic_application()
    draw_ui_state_map()
    draw_c4_container()
    draw_deployment()
    draw_layers()
    draw_sequence()
    draw_er_diagram()
    draw_uml_domain_classes()
    draw_uml_dto_classes()
    draw_benchmark_charts()
    print("==> Усі інженерні діаграми збережено у docs/diagrams/ (.svg та .png)")


if __name__ == "__main__":
    main()
