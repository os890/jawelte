#!/usr/bin/env python3
# Copyright 2026 os890
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""
Render an HTML overview of an LNP sweep from a verify-all.sh log file.

Reads the log line-by-line, segments per phase (Phase 02 = OWB, Phase
03 = Weld), parses every `[perf] <Class> methods=N total=Xms
median=Yms heap-delta=ZMB` line, groups by scenario-kind via the
class-name prefix, computes simple aggregates (cold-max, avg-all,
avg-warm-only, p50 of total ms), and writes a static index.html
suitable for opening in a browser. No third-party deps; stdlib
only.

Usage:
    lnp-report.py <input.log> <output.html>
"""
import html
import re
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path


SCENARIO_KINDS = [
    ("FullCrudScenario",            "scenario-01 - programmatic"),
    ("FullCrudDbUnitScenario",      "scenario-02 - db-unit"),
    ("FullCrudAllModulesScenario",  "scenario-03 - db-unit + all framework modules"),
    ("FullCrudJtaScenario",         "scenario-04 - programmatic + JTA"),
    ("FullCrudRestDbUnitScenario",  "scenario-05 - db-unit + REST"),
    ("FullCrudRoundtripScenario",   "scenario-06 - REST roundtrip"),
    ("FullCrudGatlingScenario",     "scenario-07 - Gatling client"),
]

PHASE_PATTERN = re.compile(
    r"^  Phase\s+(\d+):\s+tests/lnp-module(?:/\S+)?\s+\[([^]]+)\]")
PERF_PATTERN = re.compile(
    r"^\[perf\]\s+(\S+)\s+methods=(\d+)\s+total=(\d+)ms"
    r"\s+median=(\d+)ms\s+heap-delta=([+-]?[\d,\.]+)MB")
BANNER_PATTERN = re.compile(
    r"^\s*(LNP PASS GREEN|ALL \d+ PHASES GREEN|WIP PASS GREEN|>>> FAILED)")
# `=== LNP Performance Summary (<tag>) ===` - the tag identifies the
# scenario (scenario-01 prints the bare title with no tag).
SUMMARY_HEADER_PATTERN = re.compile(
    r"^=== LNP Performance Summary(?:\s+\(([^)]+)\))? ===")
SUMMARY_TAG_TO_LABEL = {
    None: "scenario-01 - programmatic",
    "db-unit": "scenario-02 - db-unit",
    "db-unit + all modules": "scenario-03 - db-unit + all framework modules",
    "jpa + jta": "scenario-04 - programmatic + JTA",
    "db-unit + REST": "scenario-05 - db-unit + REST",
    "roundtrip": "scenario-06 - REST roundtrip",
    "gatling": "scenario-07 - Gatling client",
}
# Per-row line: `<ClassName> <methods> <total> <median> <heapStart> <heapEnd> <heapDelta>`
# heap fields use the JVM's default locale (German `,`); we normalise
# downstream.
SUMMARY_ROW_PATTERN = re.compile(
    r"^(\S+)\s+(\d+)\s+(\d+)\s+(\d+)\s+"
    r"([\d,\.]+)\s+([\d,\.]+)\s+([+-]?[\d,\.]+)\s*$")


def scenario_key(class_name):
    """Pick the longest-matching scenario prefix so the more specific
    `FullCrudDbUnit` and `FullCrudAllModules` win over the bare
    `FullCrud` prefix."""
    best = None
    for prefix, label in SCENARIO_KINDS:
        if class_name.startswith(prefix):
            if best is None or len(prefix) > len(best[0]):
                best = (prefix, label)
    return best


def _parse_float(raw):
    try:
        return float(raw.replace(",", "."))
    except ValueError:
        return 0.0


def parse(log_path):
    runtime_now = "unknown"
    overall_banner = None
    # Aggregations off the per-class [perf] line.
    totals = defaultdict(lambda: defaultdict(list))
    medians = defaultdict(lambda: defaultdict(list))
    heaps = defaultdict(lambda: defaultdict(list))
    methods = defaultdict(lambda: defaultdict(list))
    # Memory history off the printed summary tables (after each
    # scenario module's FinalSummaryTest). One entry per class in
    # execution order: heap-end MB at @AfterAll. Keyed by
    # (runtime, scenario_label).
    heap_end_series = defaultdict(lambda: defaultdict(list))

    summary_state = {"label": None, "in_rows": False}
    with open(log_path, "r", encoding="utf-8", errors="replace") as fp:
        for line in fp:
            phase_match = PHASE_PATTERN.match(line)
            if phase_match:
                profiles = phase_match.group(2)
                if "owb" in profiles:
                    runtime_now = "OWB"
                elif "weld" in profiles:
                    runtime_now = "Weld"
                else:
                    runtime_now = profiles
                continue
            banner_match = BANNER_PATTERN.search(line)
            if banner_match:
                overall_banner = banner_match.group(1)
                continue
            perf_match = PERF_PATTERN.match(line)
            if perf_match:
                class_name = perf_match.group(1)
                method_count = int(perf_match.group(2))
                total_ms = int(perf_match.group(3))
                median_ms = int(perf_match.group(4))
                heap_mb = _parse_float(perf_match.group(5))
                key = scenario_key(class_name)
                if key is None:
                    continue
                _, label = key
                totals[runtime_now][label].append(total_ms)
                medians[runtime_now][label].append(median_ms)
                heaps[runtime_now][label].append(heap_mb)
                methods[runtime_now][label].append(method_count)
                continue
            sum_match = SUMMARY_HEADER_PATTERN.match(line)
            if sum_match:
                tag = sum_match.group(1)
                summary_state["label"] = SUMMARY_TAG_TO_LABEL.get(tag)
                summary_state["in_rows"] = False
                continue
            if summary_state["label"] is not None:
                # We expect a header line + a separator before rows
                # start; rows match SUMMARY_ROW_PATTERN; an empty line
                # closes the block (the trailing separator dashes
                # don't, otherwise we'd exit immediately after the
                # header).
                if line.strip().startswith("Class "):
                    summary_state["in_rows"] = True
                    continue
                if summary_state["in_rows"]:
                    if line.startswith("---"):
                        # header- or footer-separator; stay in rows
                        continue
                    row_match = SUMMARY_ROW_PATTERN.match(line.strip())
                    if row_match:
                        heap_end_mb = _parse_float(row_match.group(6))
                        heap_end_series[runtime_now][
                            summary_state["label"]].append(heap_end_mb)
                        continue
                    if line.strip() == "" or line.startswith("==="):
                        summary_state["label"] = None
                        summary_state["in_rows"] = False
    return (
        totals, medians, heaps, methods,
        heap_end_series, overall_banner)


def aggregates(samples):
    samples = sorted(samples)
    n = len(samples)
    if n == 0:
        return None
    avg_all = sum(samples) / n
    warm_avg = sum(samples[:-1]) / (n - 1) if n > 1 else samples[0]
    return {
        "n": n,
        "min": samples[0],
        "p50": samples[n // 2],
        "avg": avg_all,
        "warm_avg": warm_avg,
        "max": samples[-1],
    }


def render(totals, medians, heaps, methods, heap_end_series,
           banner, log_path, out_path):
    runtimes_present = [
        r for r in ("OWB", "Weld") if r in totals]
    scenario_order = [label for _, label in SCENARIO_KINDS]

    rows_html = []
    for label in scenario_order:
        per_runtime = []
        for runtime in runtimes_present:
            samples = totals[runtime].get(label, [])
            agg = aggregates(samples)
            per_runtime.append((runtime, agg))
        any_data = any(agg for _, agg in per_runtime)
        if not any_data:
            continue
        rows_html.append(_row_html(label, per_runtime))
    overall_table = _overall_html(totals, runtimes_present, scenario_order)
    delta_html = _delta_html(totals, runtimes_present, scenario_order)
    per_method_html = _per_method_html(
        medians, methods, runtimes_present, scenario_order)
    heap_html = _heap_html(heaps, runtimes_present, scenario_order)
    heap_chart_html = _heap_chart_html(
        heap_end_series, runtimes_present, scenario_order)

    body = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<title>jawelte LNP report</title>
<style>
body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI",
       sans-serif; margin: 2rem; color: #222; }}
h1, h2 {{ font-weight: 600; }}
h1 {{ margin-bottom: 0; }}
.subtitle {{ color: #666; margin-top: 0.2rem; }}
.banner-ok {{ background: #1f7a1f; color: white; padding: 0.4em 0.8em;
              border-radius: 4px; display: inline-block; margin: 0.4rem 0; }}
.banner-fail {{ background: #b00020; color: white; padding: 0.4em 0.8em;
                border-radius: 4px; display: inline-block; margin: 0.4rem 0; }}
table {{ border-collapse: collapse; margin: 1rem 0; min-width: 60ch; }}
th, td {{ border: 1px solid #ccc; padding: 0.4rem 0.7rem;
          text-align: right; font-variant-numeric: tabular-nums; }}
th:first-child, td:first-child {{ text-align: left; }}
thead th {{ background: #f5f5f5; }}
.scenario {{ font-weight: 600; }}
.runtime-block + .runtime-block {{ margin-top: 1rem; }}
.delta-pos {{ color: #b06000; }}
.delta-neg {{ color: #1f7a1f; }}
.meta {{ color: #666; font-size: 0.85rem; }}
</style>
</head>
<body>
<h1>jawelte LNP report</h1>
<p class="subtitle">Generated {html.escape(datetime.now().isoformat(timespec='seconds'))}
   from <code>{html.escape(str(log_path))}</code></p>
{_banner_html(banner)}
<h2>Per-scenario per-runtime aggregate (total ms per class)</h2>
{_per_scenario_table(rows_html, runtimes_present)}
<h2>Overall across runtimes (warm-class averages)</h2>
{overall_table}
<h2>Pairwise deltas (warm-class averages)</h2>
{delta_html}
<h2>Per-method runtime (median per @Test method, ms)</h2>
{per_method_html}
<h2>Heap-delta summary per scenario (MB, per-class delta)</h2>
{heap_html}
<h2>Heap-end history per scenario (MB at @AfterAll, in execution order)</h2>
{heap_chart_html}
<p class="meta">cold = highest single class total (typically the
first class JIT-cold). avg-all = mean across every class. warm =
mean excluding the cold class. p50 = median of class totals.
Per-method numbers are the median that PerformanceExtension records
per scenario class - this excludes the bootstrap dominance and is
the closest proxy for "per @Test method cost". Heap delta is the
signed difference between heap-used at @AfterAll and @BeforeAll for
each class; negative values reflect GC during the class run. The
heap-end history charts plot the absolute heap-used at @AfterAll for
each class in execution order; a saw-tooth pattern with no upward
trend is healthy GC behaviour, a monotonic upward trend would
indicate a leak.</p>
</body>
</html>
"""
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(body, encoding="utf-8")


def _banner_html(banner):
    if not banner:
        return ""
    css_class = "banner-fail" if banner.startswith(">>>") else "banner-ok"
    return f'<p class="{css_class}">{html.escape(banner)}</p>'


def _row_html(label, per_runtime):
    cells = [f'<td class="scenario">{html.escape(label)}</td>']
    for runtime, agg in per_runtime:
        if agg is None:
            cells.append('<td colspan="5">-</td>')
            continue
        cells.append(f'<td>{agg["n"]}</td>')
        cells.append(f'<td>{agg["max"]}</td>')
        cells.append(f'<td>{agg["avg"]:.0f}</td>')
        cells.append(f'<td>{agg["warm_avg"]:.0f}</td>')
        cells.append(f'<td>{agg["p50"]}</td>')
    return "<tr>" + "".join(cells) + "</tr>"


def _per_method_html(medians, methods, runtimes_present, scenario_order):
    rows = []
    for label in scenario_order:
        pooled_medians = []
        pooled_method_counts = []
        for runtime in runtimes_present:
            pooled_medians.extend(medians[runtime].get(label, []))
            pooled_method_counts.extend(methods[runtime].get(label, []))
        if not pooled_medians:
            continue
        n = len(pooled_medians)
        avg_med = sum(pooled_medians) / n
        sorted_med = sorted(pooled_medians)
        med_of_med = sorted_med[n // 2]
        avg_methods = (sum(pooled_method_counts) / len(pooled_method_counts)
                       if pooled_method_counts else 0)
        rows.append(
            f'<tr><td class="scenario">{html.escape(label)}</td>'
            f'<td>{n}</td>'
            f'<td>{avg_methods:.0f}</td>'
            f'<td>{avg_med:.1f}</td>'
            f'<td>{med_of_med}</td>'
            f'<td>{sorted_med[0]}</td>'
            f'<td>{sorted_med[-1]}</td></tr>')
    if not rows:
        return "<p>no data</p>"
    return (
        '<table><thead><tr>'
        '<th>scenario</th><th>classes</th><th>@Test methods / class</th>'
        '<th>avg median ms</th><th>median of medians ms</th>'
        '<th>min ms</th><th>max ms</th>'
        '</tr></thead><tbody>'
        + "".join(rows) + '</tbody></table>')


def _heap_html(heaps, runtimes_present, scenario_order):
    rows = []
    for label in scenario_order:
        pooled = []
        for runtime in runtimes_present:
            pooled.extend(heaps[runtime].get(label, []))
        if not pooled:
            continue
        n = len(pooled)
        avg_h = sum(pooled) / n
        sorted_h = sorted(pooled)
        median_h = sorted_h[n // 2]
        sign = "+" if avg_h > 0 else ""
        rows.append(
            f'<tr><td class="scenario">{html.escape(label)}</td>'
            f'<td>{n}</td>'
            f'<td>{sign}{avg_h:.1f}</td>'
            f'<td>{median_h:+.1f}</td>'
            f'<td>{sorted_h[0]:+.1f}</td>'
            f'<td>{sorted_h[-1]:+.1f}</td></tr>')
    if not rows:
        return "<p>no data</p>"
    return (
        '<table><thead><tr>'
        '<th>scenario</th><th>classes</th>'
        '<th>avg delta MB</th><th>median delta MB</th>'
        '<th>min delta MB</th><th>max delta MB</th>'
        '</tr></thead><tbody>'
        + "".join(rows) + '</tbody></table>')


def _per_scenario_table(rows_html, runtimes_present):
    head_runtime = "".join(
        f'<th colspan="5">{html.escape(r)}</th>' for r in runtimes_present)
    sub_per = "<th>n</th><th>cold</th><th>avg</th><th>warm</th><th>p50</th>"
    sub_head = "".join(sub_per for _ in runtimes_present)
    return (
        '<table><thead>'
        f'<tr><th rowspan="2">scenario</th>{head_runtime}</tr>'
        f'<tr>{sub_head}</tr>'
        '</thead><tbody>'
        + "".join(rows_html) + '</tbody></table>')


def _overall_html(data, runtimes_present, scenario_order):
    rows = []
    for label in scenario_order:
        samples = []
        for runtime in runtimes_present:
            samples.extend(data[runtime].get(label, []))
        agg = aggregates(samples)
        if agg is None:
            continue
        rows.append(
            f'<tr><td class="scenario">{html.escape(label)}</td>'
            f'<td>{agg["n"]}</td>'
            f'<td>{agg["max"]}</td>'
            f'<td>{agg["avg"]:.0f}</td>'
            f'<td>{agg["warm_avg"]:.0f}</td>'
            f'<td>{agg["p50"]}</td></tr>')
    return (
        '<table><thead><tr>'
        '<th>scenario</th><th>n</th><th>cold</th>'
        '<th>avg</th><th>warm</th><th>p50</th>'
        '</tr></thead><tbody>'
        + "".join(rows) + '</tbody></table>')


def _delta_html(data, runtimes_present, scenario_order):
    overall = {}
    for label in scenario_order:
        samples = []
        for runtime in runtimes_present:
            samples.extend(data[runtime].get(label, []))
        agg = aggregates(samples)
        if agg is not None:
            overall[label] = agg["warm_avg"]
    if not overall:
        return "<p>no data</p>"
    rows = []
    labels_with_data = [l for l in scenario_order if l in overall]
    if not labels_with_data:
        return "<p>no data</p>"
    base = labels_with_data[0]
    base_value = overall[base]
    for label in labels_with_data:
        value = overall[label]
        delta = value - base_value
        pct = 100 * delta / base_value if base_value else 0
        sign_class = "delta-pos" if delta > 0 else (
            "delta-neg" if delta < 0 else "")
        sign = "+" if delta > 0 else ""
        rows.append(
            f'<tr><td class="scenario">{html.escape(label)}</td>'
            f'<td>{value:.0f}</td>'
            f'<td class="{sign_class}">{sign}{delta:.0f}</td>'
            f'<td class="{sign_class}">{sign}{pct:.1f} %</td></tr>')
    return (
        '<table><thead><tr>'
        f'<th>scenario (vs {html.escape(base)})</th>'
        '<th>warm avg ms/class</th>'
        '<th>delta ms</th><th>delta %</th>'
        '</tr></thead><tbody>'
        + "".join(rows) + '</tbody></table>')


def _heap_chart_html(series, runtimes_present, scenario_order):
    blocks = []
    runtime_colour = {"OWB": "#1f6fb4", "Weld": "#c97a2b"}
    for label in scenario_order:
        runtime_lines = []
        for runtime in runtimes_present:
            values = series[runtime].get(label, [])
            if values:
                runtime_lines.append((runtime, values))
        if not runtime_lines:
            continue
        blocks.append(
            f'<h3>{html.escape(label)}</h3>'
            + _svg_line_chart(runtime_lines, runtime_colour))
    if not blocks:
        return "<p>no data</p>"
    return "".join(blocks)


def _svg_line_chart(runtime_lines, runtime_colour):
    width, height = 700, 220
    padding_left, padding_right = 50, 110
    padding_top, padding_bottom = 18, 28
    plot_w = width - padding_left - padding_right
    plot_h = height - padding_top - padding_bottom
    max_x = max((len(values) for _, values in runtime_lines), default=1)
    all_values = [v for _, values in runtime_lines for v in values]
    if not all_values:
        return ""
    min_y = min(all_values)
    max_y = max(all_values)
    if max_y == min_y:
        max_y = min_y + 1.0

    def x_of(idx):
        if max_x <= 1:
            return padding_left
        return padding_left + (idx / (max_x - 1)) * plot_w

    def y_of(val):
        return padding_top + (1.0 - (val - min_y) / (max_y - min_y)) * plot_h

    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" '
        f'height="{height}" role="img" '
        f'aria-label="heap-end history">'
    ]
    # Plot area background.
    parts.append(
        f'<rect x="{padding_left}" y="{padding_top}" '
        f'width="{plot_w}" height="{plot_h}" fill="#fafafa" '
        f'stroke="#ddd"/>')
    # Y-axis ticks: min, mid, max.
    ticks = [min_y, (min_y + max_y) / 2.0, max_y]
    for value in ticks:
        y = y_of(value)
        parts.append(
            f'<line x1="{padding_left}" y1="{y:.1f}" '
            f'x2="{padding_left + plot_w}" y2="{y:.1f}" '
            f'stroke="#eee" stroke-dasharray="2,3"/>')
        parts.append(
            f'<text x="{padding_left - 6:.1f}" y="{y + 4:.1f}" '
            f'font-size="10" text-anchor="end" fill="#666">'
            f'{value:.0f} MB</text>')
    # X-axis labels (start, mid, end).
    for idx_label in (0, (max_x - 1) // 2, max_x - 1):
        if idx_label < 0:
            continue
        x = x_of(idx_label)
        parts.append(
            f'<text x="{x:.1f}" y="{padding_top + plot_h + 14:.1f}" '
            f'font-size="10" text-anchor="middle" fill="#666">'
            f'{idx_label + 1}</text>')
    parts.append(
        f'<text x="{padding_left + plot_w / 2:.1f}" '
        f'y="{height - 4}" font-size="10" '
        f'text-anchor="middle" fill="#444">class execution order</text>')
    # One polyline per runtime.
    for runtime, values in runtime_lines:
        colour = runtime_colour.get(runtime, "#444")
        points = " ".join(
            f"{x_of(i):.1f},{y_of(v):.1f}"
            for i, v in enumerate(values))
        parts.append(
            f'<polyline fill="none" stroke="{colour}" '
            f'stroke-width="1.4" points="{points}"/>')
    # Legend at the right.
    legend_x = padding_left + plot_w + 16
    legend_y = padding_top
    for runtime, _ in runtime_lines:
        colour = runtime_colour.get(runtime, "#444")
        parts.append(
            f'<line x1="{legend_x}" y1="{legend_y + 5}" '
            f'x2="{legend_x + 16}" y2="{legend_y + 5}" '
            f'stroke="{colour}" stroke-width="2"/>')
        parts.append(
            f'<text x="{legend_x + 20}" y="{legend_y + 9}" '
            f'font-size="11" fill="#333">{html.escape(runtime)}</text>')
        legend_y += 16
    parts.append('</svg>')
    return "".join(parts)


def main():
    if len(sys.argv) != 3:
        sys.stderr.write("usage: lnp-report.py <input.log> <output.html>\n")
        sys.exit(2)
    log_path = Path(sys.argv[1])
    out_path = Path(sys.argv[2])
    if not log_path.exists():
        sys.stderr.write(f"log file not found: {log_path}\n")
        sys.exit(2)
    (totals, medians, heaps, methods,
     heap_end_series, banner) = parse(log_path)
    render(totals, medians, heaps, methods, heap_end_series,
           banner, log_path, out_path)
    print(f"wrote {out_path}")


if __name__ == "__main__":
    main()
