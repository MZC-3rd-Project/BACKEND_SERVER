#!/usr/bin/env python3
"""Generate simple SVG charts for DB lock contention benchmark summaries.

Usage:
  python3 docker/benchmark/generate_lock_contention_svg.py \
    --out-dir docs/assets/db-lock-contention-2026-03-02 \
    /tmp/db_lock_contention_compare_20260302_155623/db_lock_contention_summary.json \
    /tmp/db_lock_contention_compare_20260302_155814/db_lock_contention_summary.json \
    /tmp/db_lock_contention_compare_20260302_155952/db_lock_contention_summary.json
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Dict, List, Tuple


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate SVG charts from lock contention summaries")
    parser.add_argument("summary_json", nargs="+", help="Paths to db_lock_contention_summary.json")
    parser.add_argument("--out-dir", required=True, help="Output directory for svg/json/csv artifacts")
    return parser.parse_args()


def load_rows(paths: List[str]) -> List[Dict]:
    rows: List[Dict] = []
    for idx, path in enumerate(paths, start=1):
        with open(path, "r", encoding="utf-8") as f:
            doc = json.load(f)
        for case in doc["cases"]:
            rows.append(
                {
                    "run": f"run{idx}",
                    "case": case["case"],
                    "write_p95_ms": float(case["write_p95_ms"]),
                    "write_avg_ms": float(case["write_avg_ms"]),
                    "read_p95_ms": float(case["read_p95_ms"]),
                    "writer_lock_wait_sessions_avg": float(case["writer_lock_wait_sessions_avg"]),
                    "writer_lock_wait_sessions_max": float(case["writer_lock_wait_sessions_max"]),
                    "writer_waiting_locks_max": float(case["writer_waiting_locks_max"]),
                    "writer_cpu_avg_percent": float(case["writer_cpu_avg_percent"]),
                    "writer_xact_delta": float(case["writer_xact_delta"]),
                    "read_xact_delta": float(case["read_xact_delta"]),
                }
            )
    return rows


def summarize_by_case(rows: List[Dict]) -> Dict[str, Dict[str, float]]:
    by_case: Dict[str, List[Dict]] = {}
    for row in rows:
        by_case.setdefault(row["case"], []).append(row)

    metrics = [
        "write_p95_ms",
        "writer_lock_wait_sessions_avg",
        "writer_lock_wait_sessions_max",
        "writer_waiting_locks_max",
        "writer_cpu_avg_percent",
        "writer_xact_delta",
        "read_xact_delta",
    ]
    summary: Dict[str, Dict[str, float]] = {}
    for case, items in by_case.items():
        summary[case] = {}
        for metric in metrics:
            vals = [float(x[metric]) for x in items]
            summary[case][f"{metric}_avg"] = sum(vals) / len(vals)
            summary[case][f"{metric}_min"] = min(vals)
            summary[case][f"{metric}_max"] = max(vals)
    return summary


def write_json(path: Path, data: Dict) -> None:
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False), encoding="utf-8")


def write_csv(path: Path, rows: List[Dict]) -> None:
    cols = [
        "run",
        "case",
        "write_p95_ms",
        "write_avg_ms",
        "read_p95_ms",
        "writer_lock_wait_sessions_avg",
        "writer_lock_wait_sessions_max",
        "writer_waiting_locks_max",
        "writer_cpu_avg_percent",
        "writer_xact_delta",
        "read_xact_delta",
    ]
    lines = [",".join(cols)]
    for row in rows:
        lines.append(",".join(str(row[col]) for col in cols))
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def bar_chart_svg(
    title: str,
    labels: List[str],
    values: List[float],
    unit: str,
    out_path: Path,
    color: str = "#2f80ed",
) -> None:
    width = 1000
    height = 500
    left = 80
    right = 40
    top = 70
    bottom = 90
    chart_w = width - left - right
    chart_h = height - top - bottom

    max_val = max(values) if values else 1.0
    if max_val <= 0:
        max_val = 1.0
    y_max = max_val * 1.15

    bar_count = len(values)
    slot = chart_w / max(bar_count, 1)
    bar_w = slot * 0.55

    parts: List[str] = []
    parts.append(f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}">')
    parts.append('<rect width="100%" height="100%" fill="#ffffff"/>')
    parts.append(f'<text x="{width//2}" y="34" text-anchor="middle" font-size="24" font-family="Arial" fill="#111">{title}</text>')
    parts.append(f'<line x1="{left}" y1="{top}" x2="{left}" y2="{top+chart_h}" stroke="#444" stroke-width="1"/>')
    parts.append(f'<line x1="{left}" y1="{top+chart_h}" x2="{left+chart_w}" y2="{top+chart_h}" stroke="#444" stroke-width="1"/>')

    for i in range(5):
        frac = i / 4
        y_val = y_max * (1 - frac)
        y = top + chart_h * frac
        parts.append(f'<line x1="{left}" y1="{y:.2f}" x2="{left+chart_w}" y2="{y:.2f}" stroke="#eee" stroke-width="1"/>')
        parts.append(
            f'<text x="{left-10}" y="{y+4:.2f}" text-anchor="end" font-size="12" font-family="Arial" fill="#666">{y_val:.1f}{unit}</text>'
        )

    for idx, (label, val) in enumerate(zip(labels, values)):
        x = left + idx * slot + (slot - bar_w) / 2
        h = (val / y_max) * chart_h
        y = top + chart_h - h
        parts.append(f'<rect x="{x:.2f}" y="{y:.2f}" width="{bar_w:.2f}" height="{h:.2f}" fill="{color}" rx="4" ry="4"/>')
        parts.append(
            f'<text x="{x+bar_w/2:.2f}" y="{y-8:.2f}" text-anchor="middle" font-size="12" font-family="Arial" fill="#222">{val:.2f}</text>'
        )
        parts.append(
            f'<text x="{x+bar_w/2:.2f}" y="{top+chart_h+24:.2f}" text-anchor="middle" font-size="12" font-family="Arial" fill="#333">{label}</text>'
        )

    parts.append("</svg>")
    out_path.write_text("\n".join(parts), encoding="utf-8")


def grouped_run_chart_svg(
    title: str,
    run_labels: List[str],
    single_vals: List[float],
    split_vals: List[float],
    unit: str,
    out_path: Path,
) -> None:
    width = 1000
    height = 500
    left = 80
    right = 40
    top = 70
    bottom = 100
    chart_w = width - left - right
    chart_h = height - top - bottom

    max_val = max(single_vals + split_vals) if single_vals else 1.0
    if max_val <= 0:
        max_val = 1.0
    y_max = max_val * 1.20

    group_count = len(run_labels)
    slot = chart_w / max(group_count, 1)
    group_w = slot * 0.7
    bar_w = group_w / 2.4

    parts: List[str] = []
    parts.append(f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}">')
    parts.append('<rect width="100%" height="100%" fill="#ffffff"/>')
    parts.append(f'<text x="{width//2}" y="34" text-anchor="middle" font-size="24" font-family="Arial" fill="#111">{title}</text>')
    parts.append(f'<line x1="{left}" y1="{top}" x2="{left}" y2="{top+chart_h}" stroke="#444" stroke-width="1"/>')
    parts.append(f'<line x1="{left}" y1="{top+chart_h}" x2="{left+chart_w}" y2="{top+chart_h}" stroke="#444" stroke-width="1"/>')

    for i in range(5):
        frac = i / 4
        y_val = y_max * (1 - frac)
        y = top + chart_h * frac
        parts.append(f'<line x1="{left}" y1="{y:.2f}" x2="{left+chart_w}" y2="{y:.2f}" stroke="#eee" stroke-width="1"/>')
        parts.append(
            f'<text x="{left-10}" y="{y+4:.2f}" text-anchor="end" font-size="12" font-family="Arial" fill="#666">{y_val:.1f}{unit}</text>'
        )

    for idx, run in enumerate(run_labels):
        cx = left + idx * slot + slot / 2
        group_left = cx - group_w / 2

        s_val = single_vals[idx]
        r_val = split_vals[idx]
        s_h = (s_val / y_max) * chart_h
        r_h = (r_val / y_max) * chart_h
        s_y = top + chart_h - s_h
        r_y = top + chart_h - r_h

        s_x = group_left + (group_w / 2 - bar_w) / 2
        r_x = group_left + group_w / 2 + (group_w / 2 - bar_w) / 2

        parts.append(f'<rect x="{s_x:.2f}" y="{s_y:.2f}" width="{bar_w:.2f}" height="{s_h:.2f}" fill="#eb5757" rx="4" ry="4"/>')
        parts.append(f'<rect x="{r_x:.2f}" y="{r_y:.2f}" width="{bar_w:.2f}" height="{r_h:.2f}" fill="#2d9cdb" rx="4" ry="4"/>')

        parts.append(
            f'<text x="{s_x+bar_w/2:.2f}" y="{s_y-8:.2f}" text-anchor="middle" font-size="11" font-family="Arial" fill="#222">{s_val:.2f}</text>'
        )
        parts.append(
            f'<text x="{r_x+bar_w/2:.2f}" y="{r_y-8:.2f}" text-anchor="middle" font-size="11" font-family="Arial" fill="#222">{r_val:.2f}</text>'
        )

        parts.append(
            f'<text x="{cx:.2f}" y="{top+chart_h+24:.2f}" text-anchor="middle" font-size="12" font-family="Arial" fill="#333">{run}</text>'
        )

    parts.append('<rect x="740" y="18" width="14" height="14" fill="#eb5757"/>')
    parts.append('<text x="760" y="30" font-size="12" font-family="Arial" fill="#333">single_db</text>')
    parts.append('<rect x="860" y="18" width="14" height="14" fill="#2d9cdb"/>')
    parts.append('<text x="880" y="30" font-size="12" font-family="Arial" fill="#333">split_read</text>')

    parts.append("</svg>")
    out_path.write_text("\n".join(parts), encoding="utf-8")


def main() -> None:
    args = parse_args()
    out_dir = Path(args.out_dir).resolve()
    out_dir.mkdir(parents=True, exist_ok=True)

    rows = load_rows(args.summary_json)
    rows_sorted = sorted(rows, key=lambda x: (x["run"], x["case"]))
    summary = summarize_by_case(rows_sorted)

    artifact = {"runs": rows_sorted, "summary_by_case": summary}
    write_json(out_dir / "lock_contention_aggregated.json", artifact)
    write_csv(out_dir / "lock_contention_runs.csv", rows_sorted)

    single = summary["single_db"]
    split = summary["split_read"]

    bar_chart_svg(
        title="Writer CPU Average (%) - 3 Runs Mean",
        labels=["single_db", "split_read"],
        values=[single["writer_cpu_avg_percent_avg"], split["writer_cpu_avg_percent_avg"]],
        unit="%",
        out_path=out_dir / "chart_writer_cpu_avg.svg",
        color="#27ae60",
    )

    bar_chart_svg(
        title="Writer Lock Wait Sessions (avg) - 3 Runs Mean",
        labels=["single_db", "split_read"],
        values=[single["writer_lock_wait_sessions_avg_avg"], split["writer_lock_wait_sessions_avg_avg"]],
        unit="",
        out_path=out_dir / "chart_lock_wait_avg.svg",
        color="#f2994a",
    )

    bar_chart_svg(
        title="Writer vs Read Xact Delta - 3 Runs Mean",
        labels=["single_db writer", "single_db read", "split_read writer", "split_read read"],
        values=[
            single["writer_xact_delta_avg"],
            single["read_xact_delta_avg"],
            split["writer_xact_delta_avg"],
            split["read_xact_delta_avg"],
        ],
        unit="",
        out_path=out_dir / "chart_xact_shift.svg",
        color="#9b51e0",
    )

    runs = sorted(list({r["run"] for r in rows_sorted}))
    single_vals: List[float] = []
    split_vals: List[float] = []
    for run in runs:
        r_single = next(r for r in rows_sorted if r["run"] == run and r["case"] == "single_db")
        r_split = next(r for r in rows_sorted if r["run"] == run and r["case"] == "split_read")
        single_vals.append(r_single["write_p95_ms"])
        split_vals.append(r_split["write_p95_ms"])

    grouped_run_chart_svg(
        title="Write p95 (ms) by Run",
        run_labels=runs,
        single_vals=single_vals,
        split_vals=split_vals,
        unit="ms",
        out_path=out_dir / "chart_write_p95_runs.svg",
    )

    print(f"[OK] charts generated in: {out_dir}")
    for name in [
        "lock_contention_aggregated.json",
        "lock_contention_runs.csv",
        "chart_writer_cpu_avg.svg",
        "chart_lock_wait_avg.svg",
        "chart_xact_shift.svg",
        "chart_write_p95_runs.svg",
    ]:
        print(str(out_dir / name))


if __name__ == "__main__":
    main()
