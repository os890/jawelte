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
Render an HTML overview of a verify-all.sh full-mode run.

Reads target/verify-report/data/<combo-slug>/<module-slug>/, where
each combo-slug is the active Maven profile combination with commas
replaced by dashes ("owb", "weld", "quarkus", "owb-jta-narayana",
"owb-cxf", "default" for the no-profile phases), each module-slug is
the test aggregator's directory name (cdi-module, jta-module, ...),
and each scenario subdir contains the surefire TEST-*.xml files
that Maven wrote during that combo's verify pass. Each combo-module
dir also has a `_meta.txt` capturing the wall-clock duration and
exit code of the phase.

Writes index.html with:
  * a header banner (all green / N failed)
  * a sortable summary table (combo  -> modules / tests / passed /
    failed / skipped / time)
  * one collapsible <details> section per combo, listing modules
    and scenarios with per-class pass/fail/duration

No third-party deps; stdlib only.

Usage:
    verify-report.py <data-root> <output.html>
"""
import html
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Optional, Tuple


@dataclass
class TestCase:
    classname: str
    name: str
    time: float
    outcome: str   # "pass" | "fail" | "error" | "skipped"
    detail: Optional[str] = None


@dataclass
class TestClass:
    """One surefire TEST-*.xml file (one test class)."""
    classname: str
    time: float
    tests: int
    failures: int
    errors: int
    skipped: int
    cases: List[TestCase] = field(default_factory=list)

    @property
    def passed(self) -> int:
        return self.tests - self.failures - self.errors - self.skipped

    @property
    def ok(self) -> bool:
        return self.failures == 0 and self.errors == 0


@dataclass
class Scenario:
    slug: str
    classes: List[TestClass] = field(default_factory=list)

    @property
    def time(self) -> float:
        return sum(c.time for c in self.classes)

    @property
    def tests(self) -> int:
        return sum(c.tests for c in self.classes)

    @property
    def passed(self) -> int:
        return sum(c.passed for c in self.classes)

    @property
    def failed(self) -> int:
        return sum(c.failures + c.errors for c in self.classes)

    @property
    def skipped(self) -> int:
        return sum(c.skipped for c in self.classes)

    @property
    def ok(self) -> bool:
        return self.failed == 0


@dataclass
class Module:
    slug: str
    label: str
    duration: int
    exit_code: int
    scenarios: List[Scenario] = field(default_factory=list)

    @property
    def tests(self) -> int:
        return sum(s.tests for s in self.scenarios)

    @property
    def passed(self) -> int:
        return sum(s.passed for s in self.scenarios)

    @property
    def failed(self) -> int:
        return sum(s.failed for s in self.scenarios)

    @property
    def skipped(self) -> int:
        return sum(s.skipped for s in self.scenarios)

    @property
    def ok(self) -> bool:
        return self.exit_code == 0 and self.failed == 0


@dataclass
class Combo:
    slug: str
    label_override: Optional[str] = None
    modules: List[Module] = field(default_factory=list)

    @property
    def label(self) -> str:
        # Prefer the verbatim `combo=` field captured by run() in
        # _meta.txt — the slug folds commas to dashes, so reversing
        # the slug alone can't distinguish "owb,jta-narayana" from
        # "owb-jta-narayana".
        if self.label_override:
            return self.label_override
        if self.slug == "default":
            return "(no profile)"
        return self.slug.replace("-", ", ")

    @property
    def duration(self) -> int:
        return sum(m.duration for m in self.modules)

    @property
    def tests(self) -> int:
        return sum(m.tests for m in self.modules)

    @property
    def passed(self) -> int:
        return sum(m.passed for m in self.modules)

    @property
    def failed(self) -> int:
        return sum(m.failed for m in self.modules)

    @property
    def skipped(self) -> int:
        return sum(m.skipped for m in self.modules)

    @property
    def scenario_count(self) -> int:
        return sum(len(m.scenarios) for m in self.modules)

    @property
    def module_count(self) -> int:
        # Aggregator phases with zero scenarios still count as a
        # "module that ran" if they have a _meta.txt.
        return len(self.modules)

    @property
    def ok(self) -> bool:
        return all(m.ok for m in self.modules)


def parse_meta(path: Path) -> Dict[str, str]:
    out = {}
    if not path.is_file():
        return out
    for line in path.read_text().splitlines():
        if "=" not in line:
            continue
        k, v = line.split("=", 1)
        out[k.strip()] = v.strip()
    return out


def parse_surefire_xml(path: Path) -> Optional[TestClass]:
    try:
        tree = ET.parse(path)
    except ET.ParseError:
        return None
    root = tree.getroot()
    if root.tag != "testsuite":
        return None
    classname = root.attrib.get("name", path.stem)
    try:
        time = float(root.attrib.get("time", "0") or 0)
    except ValueError:
        time = 0.0
    tests = int(root.attrib.get("tests", "0") or 0)
    failures = int(root.attrib.get("failures", "0") or 0)
    errors = int(root.attrib.get("errors", "0") or 0)
    skipped = int(root.attrib.get("skipped", "0") or 0)
    cases: List[TestCase] = []
    for tc in root.findall("testcase"):
        try:
            case_time = float(tc.attrib.get("time", "0") or 0)
        except ValueError:
            case_time = 0.0
        outcome = "pass"
        detail = None
        if tc.find("failure") is not None:
            outcome = "fail"
            detail = (tc.find("failure").attrib.get("message")
                      or tc.find("failure").attrib.get("type"))
        elif tc.find("error") is not None:
            outcome = "error"
            detail = (tc.find("error").attrib.get("message")
                      or tc.find("error").attrib.get("type"))
        elif tc.find("skipped") is not None:
            outcome = "skipped"
            detail = tc.find("skipped").attrib.get("message")
        cases.append(TestCase(
            classname=tc.attrib.get("classname", classname),
            name=tc.attrib.get("name", "?"),
            time=case_time,
            outcome=outcome,
            detail=detail))
    return TestClass(classname=classname, time=time, tests=tests,
                     failures=failures, errors=errors, skipped=skipped,
                     cases=cases)


def parse(data_root: Path) -> List[Combo]:
    combos: List[Combo] = []
    if not data_root.is_dir():
        return combos
    for combo_dir in sorted(data_root.iterdir(), key=_combo_sort_key):
        if not combo_dir.is_dir():
            continue
        combo = Combo(slug=combo_dir.name)
        for module_dir in sorted(combo_dir.iterdir()):
            if not module_dir.is_dir():
                continue
            meta = parse_meta(module_dir / "_meta.txt")
            if combo.label_override is None:
                combo_raw = meta.get("combo", "")
                if combo_raw:
                    combo.label_override = combo_raw
            module = Module(
                slug=module_dir.name,
                label=meta.get("label", module_dir.name),
                duration=int(meta.get("duration", "0") or 0),
                exit_code=int(meta.get("exit", "0") or 0))
            for scenario_dir in sorted(module_dir.iterdir()):
                if not scenario_dir.is_dir():
                    continue
                scenario = Scenario(slug=scenario_dir.name)
                for xml_file in sorted(scenario_dir.glob("TEST-*.xml")):
                    tc = parse_surefire_xml(xml_file)
                    if tc is not None:
                        scenario.classes.append(tc)
                if scenario.classes:
                    module.scenarios.append(scenario)
            combo.modules.append(module)
        combos.append(combo)
    return combos


def _combo_sort_key(combo_dir: Path) -> Tuple:
    """Order combos so the report flows from simple to complex:
    default first, then single-axis (owb / weld / quarkus), then
    multi-axis sorted alphabetically."""
    slug = combo_dir.name
    if slug == "default":
        return (0, slug)
    parts = slug.split("-")
    return (len(parts), slug)


def fmt_duration(seconds: float) -> str:
    s = int(round(seconds))
    if s < 60:
        return f"{s}s"
    m, s = divmod(s, 60)
    if m < 60:
        return f"{m}m {s:02d}s"
    h, m = divmod(m, 60)
    return f"{h}h {m:02d}m"


def fmt_time(secs: float) -> str:
    if secs < 0.001:
        return "0s"
    if secs < 1:
        return f"{int(round(secs * 1000))}ms"
    return f"{secs:.2f}s"


def overall_outcome(combos: List[Combo]) -> Tuple[bool, int]:
    failed = sum(c.failed for c in combos)
    aborted = sum(1 for c in combos for m in c.modules if m.exit_code != 0)
    return failed == 0 and aborted == 0, failed + aborted


def render(combos: List[Combo], data_root: Path, output_path: Path) -> None:
    ok, fail_count = overall_outcome(combos)
    banner = ('<span class="banner-ok">all green</span>' if ok
              else f'<span class="banner-fail">{fail_count} failure(s)</span>')

    summary_rows = []
    for combo in combos:
        scen_total = combo.scenario_count
        modules_run = combo.module_count
        row_class = "" if combo.ok else " class=\"row-fail\""
        summary_rows.append(
            f"<tr{row_class}>"
            f"<td><a href=\"#combo-{html.escape(combo.slug)}\">"
            f"{html.escape(combo.label)}</a></td>"
            f"<td>{modules_run}</td>"
            f"<td>{scen_total}</td>"
            f"<td>{combo.tests}</td>"
            f"<td>{combo.passed}</td>"
            f"<td>{combo.failed}</td>"
            f"<td>{combo.skipped}</td>"
            f"<td>{fmt_duration(combo.duration)}</td>"
            "</tr>")
    summary_table = (
        '<table class="summary">'
        '<thead><tr>'
        '<th>combo</th><th>modules</th><th>scenarios</th>'
        '<th>tests</th><th>pass</th><th>fail</th><th>skip</th><th>time</th>'
        '</tr></thead><tbody>'
        + "".join(summary_rows) + "</tbody></table>")

    sections: List[str] = []
    for combo in combos:
        sections.append(_render_combo(combo))

    total_duration = sum(c.duration for c in combos)
    total_tests = sum(c.tests for c in combos)
    total_passed = sum(c.passed for c in combos)
    total_failed = sum(c.failed for c in combos)
    total_skipped = sum(c.skipped for c in combos)

    body = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<title>jawelte verify-all report</title>
<style>
body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI",
       sans-serif; margin: 2rem; color: #222; max-width: 1400px; }}
h1, h2, h3 {{ font-weight: 600; }}
h1 {{ margin-bottom: 0; }}
.subtitle {{ color: #666; margin-top: 0.2rem; }}
.banner-ok {{ background: #1f7a1f; color: white; padding: 0.4em 0.8em;
              border-radius: 4px; display: inline-block; margin: 0.4rem 0; }}
.banner-fail {{ background: #b00020; color: white; padding: 0.4em 0.8em;
                border-radius: 4px; display: inline-block; margin: 0.4rem 0; }}
table {{ border-collapse: collapse; margin: 0.5rem 0 1.2rem 0;
         font-variant-numeric: tabular-nums; }}
th, td {{ border: 1px solid #ccc; padding: 0.35rem 0.6rem;
          text-align: right; }}
th:first-child, td:first-child {{ text-align: left; }}
thead th {{ background: #f5f5f5; }}
table.summary {{ min-width: 80ch; }}
table.summary td a {{ color: #1565c0; text-decoration: none; }}
table.summary td a:hover {{ text-decoration: underline; }}
tr.row-fail td {{ background: #fbe9e7; }}
details {{ margin: 0.6rem 0; }}
details > summary {{ cursor: pointer; padding: 0.3rem 0; font-weight: 600; }}
details.combo {{ border: 1px solid #ddd; border-radius: 4px;
                 padding: 0.4rem 0.8rem; margin-bottom: 0.8rem; }}
details.combo[data-fail="1"] {{ border-color: #b00020; background: #fff5f4; }}
details.module {{ padding-left: 1rem; margin: 0.3rem 0; }}
details.module[data-fail="1"] > summary {{ color: #b00020; }}
table.scenarios {{ margin-left: 1.5rem; min-width: 70ch; }}
table.scenarios td.cls {{ font-family: ui-monospace, SFMono-Regular,
                          Menlo, monospace; font-size: 0.85rem;
                          text-align: left; }}
.pill {{ display: inline-block; min-width: 2.5em; text-align: center;
         border-radius: 3px; padding: 0.05em 0.4em; font-size: 0.78rem;
         font-weight: 600; }}
.pill-ok   {{ background: #e8f5e9; color: #1b5e20; }}
.pill-fail {{ background: #ffebee; color: #b71c1c; }}
.pill-skip {{ background: #fff3e0; color: #e65100; }}
.pill-empty{{ background: #eceff1; color: #455a64; }}
.fail-detail {{ font-size: 0.78rem; color: #b71c1c;
                font-family: ui-monospace, monospace; }}
.combo-stats {{ color: #666; font-weight: 400; font-size: 0.9rem;
                margin-left: 0.6em; }}
.meta {{ color: #666; font-size: 0.85rem; margin-top: 2rem; }}
.toc {{ margin: 0.4rem 0 1.2rem 0; }}
.toc a {{ display: inline-block; margin-right: 0.6rem;
          color: #1565c0; text-decoration: none; font-size: 0.9rem; }}
.toc a:hover {{ text-decoration: underline; }}
</style>
</head>
<body>
<h1>jawelte verify-all report</h1>
<p class="subtitle">
  Generated {html.escape(datetime.now().isoformat(timespec='seconds'))}
  from <code>{html.escape(str(data_root))}</code>
</p>
<p>{banner}
   &nbsp;<span class="combo-stats">{len(combos)} combo(s) &middot;
   {total_tests} test(s) &middot;
   {total_passed} pass &middot;
   {total_failed} fail &middot;
   {total_skipped} skip &middot;
   total {fmt_duration(total_duration)}</span></p>

<h2>Summary</h2>
{summary_table}

<h2>Per-combo detail</h2>
<p class="toc">{
    " ".join(f'<a href="#combo-{html.escape(c.slug)}">{html.escape(c.label)}</a>'
             for c in combos)
}</p>
{"".join(sections)}

<p class="meta">
  Each combo runs every applicable test module with its
  <code>-P&lt;combo&gt;</code> profile active. The <em>scenarios</em>
  column is the count of <code>tests/&lt;module&gt;/scenario-*</code>
  subdirs that produced at least one surefire <code>TEST-*.xml</code>
  for the combo — a profile that filters surefire to
  <code>**/*QuarkusTest.java</code> with no companion tests shows up
  as a module-cell with zero scenarios. Failures keep their combo
  section auto-expanded; passing combos collapse by default.
</p>
</body>
</html>
"""
    output_path.write_text(body)


def _render_combo(combo: Combo) -> str:
    open_attr = "" if combo.ok else " open"
    fail_attr = "" if combo.ok else " data-fail=\"1\""
    pill = ("<span class=\"pill pill-ok\">PASS</span>" if combo.ok
            else "<span class=\"pill pill-fail\">FAIL</span>")
    stats = (f'<span class="combo-stats">'
             f'{combo.passed}/{combo.tests} pass'
             + (f', {combo.failed} fail' if combo.failed else '')
             + (f', {combo.skipped} skip' if combo.skipped else '')
             + f' &middot; {fmt_duration(combo.duration)}'
             '</span>')
    modules_html = "".join(_render_module(m) for m in combo.modules)
    return (f'<details class="combo" id="combo-{html.escape(combo.slug)}"'
            f'{open_attr}{fail_attr}>'
            f'<summary>{pill} {html.escape(combo.label)}{stats}</summary>'
            f'{modules_html}'
            '</details>')


def _render_module(module: Module) -> str:
    open_attr = "" if module.ok else " open"
    fail_attr = "" if module.ok else " data-fail=\"1\""
    if module.exit_code != 0:
        pill = '<span class="pill pill-fail">FAIL</span>'
    elif module.failed:
        pill = '<span class="pill pill-fail">FAIL</span>'
    elif module.tests == 0:
        pill = '<span class="pill pill-empty">0 tests</span>'
    else:
        pill = '<span class="pill pill-ok">PASS</span>'
    summary = (f'<summary>{pill} <code>{html.escape(module.slug)}</code>'
               f'<span class="combo-stats">'
               f'{module.passed}/{module.tests} pass'
               + (f', {module.failed} fail' if module.failed else '')
               + (f', {module.skipped} skip' if module.skipped else '')
               + f' &middot; {fmt_duration(module.duration)}'
               '</span></summary>')
    if not module.scenarios:
        body = ('<p style="margin-left: 1.5rem; color: #666; '
                'font-size: 0.85rem;">No scenarios produced surefire '
                'output for this combo.</p>')
    else:
        body = _render_scenarios_table(module)
    return (f'<details class="module"{open_attr}{fail_attr}>'
            f'{summary}{body}</details>')


def _render_scenarios_table(module: Module) -> str:
    rows = []
    for scenario in module.scenarios:
        if not scenario.classes:
            continue
        for cls in scenario.classes:
            outcome_pill = _outcome_pill(cls)
            row_class = "" if cls.ok else " class=\"row-fail\""
            fail_html = ""
            if not cls.ok:
                first_fail = next((c for c in cls.cases
                                   if c.outcome in ("fail", "error")), None)
                if first_fail and first_fail.detail:
                    fail_html = (f'<div class="fail-detail">'
                                 f'{html.escape(first_fail.detail[:240])}'
                                 '</div>')
            rows.append(
                f"<tr{row_class}>"
                f"<td>{html.escape(scenario.slug)}</td>"
                f"<td class=\"cls\">{html.escape(_short_classname(cls.classname))}</td>"
                f"<td>{outcome_pill}</td>"
                f"<td>{cls.passed}/{cls.tests}</td>"
                f"<td>{fmt_time(cls.time)}</td>"
                f"<td>{fail_html}</td>"
                "</tr>")
    table = (
        '<table class="scenarios"><thead><tr>'
        '<th>scenario</th><th>class</th><th>outcome</th>'
        '<th>pass/total</th><th>time</th><th>detail</th>'
        '</tr></thead><tbody>' + "".join(rows) + '</tbody></table>')
    return table


def _outcome_pill(cls: TestClass) -> str:
    if not cls.ok:
        return '<span class="pill pill-fail">FAIL</span>'
    if cls.tests == 0:
        return '<span class="pill pill-empty">empty</span>'
    if cls.skipped == cls.tests:
        return '<span class="pill pill-skip">SKIP</span>'
    return '<span class="pill pill-ok">PASS</span>'


def _short_classname(fqn: str) -> str:
    # Strip the common org.os890.jawelte.tests prefix to keep rows
    # readable. Falls back to the simple class name when the package
    # is unfamiliar.
    prefix = "org.os890.jawelte.tests."
    if fqn.startswith(prefix):
        return fqn[len(prefix):]
    return fqn


def main() -> None:
    if len(sys.argv) != 3:
        print("Usage: verify-report.py <data-root> <output.html>",
              file=sys.stderr)
        sys.exit(2)
    data_root = Path(sys.argv[1])
    output_path = Path(sys.argv[2])
    combos = parse(data_root)
    if not combos:
        print(f"verify-report.py: no combo data under {data_root}",
              file=sys.stderr)
        sys.exit(0)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    render(combos, data_root, output_path)
    print(f"wrote {output_path}")


if __name__ == "__main__":
    main()
