#!/usr/bin/env python3
"""Build one HTML page that lists the findings of the security scan jobs.

Usage:
    python3 security_report.py <results-dir> <output.html>

<results-dir> holds one folder per downloaded workflow artifact:
    sonarqube-results/  issues.json, hotspots.json, measures.json, quality-gate.json
    semgrep-results/    semgrep.json
    trivy-results/      trivy.json
    kics-results/       results.json

A missing folder or file is shown in the report as "no results" (for example when
that scan job failed). Only the Python standard library is used.
"""
import argparse
import html
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

SEVERITIES = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "INFO"]
RANK = {s: i for i, s in enumerate(SEVERITIES)}

# Each tool names its severity levels differently; map them onto SEVERITIES.
SONAR_SEVERITY = {"BLOCKER": "CRITICAL", "CRITICAL": "HIGH", "MAJOR": "MEDIUM", "MINOR": "LOW", "INFO": "INFO"}
SEMGREP_SEVERITY = {"ERROR": "HIGH", "WARNING": "MEDIUM", "INFO": "LOW"}


def normalize(severity, mapping=None):
    s = str(severity or "").upper()
    if mapping and s in mapping:
        return mapping[s]
    return s if s in RANK else "INFO"


def load_json(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except FileNotFoundError:
        return None
    except (OSError, ValueError) as e:
        print(f"warning: could not read {path}: {e}", file=sys.stderr)
        return None


# ---- table cells: (plain text used for searching, html) ----------------------

def text(value):
    v = "" if value is None else str(value)
    return v, html.escape(v)


def code(value):
    v = "" if value is None else str(value)
    return v, f"<code>{html.escape(v)}</code>" if v else ""


def link(value, url):
    v = "" if value is None else str(value)
    if not url:
        return text(v)
    return v, f'<a href="{html.escape(url, quote=True)}" target="_blank" rel="noopener">{html.escape(v)}</a>'


def location(path, line=None):
    path = str(path or "")
    return code(f"{path}:{line}" if line else path)


class Section:
    def __init__(self, key, title, description, columns):
        self.key = key
        self.title = title
        self.description = description
        self.columns = columns
        self.rows = []          # (severity, [cells])
        self.notes = []         # extra html shown above the table
        self.available = False  # False when the results file was not found

    def add(self, severity, cells):
        self.rows.append((severity, cells))

    def counts(self):
        c = {s: 0 for s in SEVERITIES}
        for sev, _ in self.rows:
            c[sev] += 1
        return c


# ---- parsers -----------------------------------------------------------------

def strip_component(component):
    # Sonar components look like "projectKey:path/to/File.java"
    return str(component or "").split(":", 1)[-1]


def parse_sonar(folder):
    issues = Section("sonar-issues", "SonarQube – Issues",
                     "Bugs, vulnerabilities and code smells found by SonarQube.",
                     ["Severity", "Type", "Rule", "Location", "Message"])
    hotspots = Section("sonar-hotspots", "SonarQube – Security hotspots",
                       "Security-sensitive code that should be reviewed by hand.",
                       ["Severity", "Category", "Status", "Location", "Message"])

    data = load_json(folder / "issues.json")
    if data is not None:
        issues.available = True
        for i in data:
            if i.get("severity"):
                sev = normalize(i["severity"], SONAR_SEVERITY)
            else:
                # Newer Sonar versions only report "impacts"; use the worst one.
                impacts = [normalize(x.get("severity"), {"BLOCKER": "CRITICAL"}) for x in i.get("impacts", [])]
                sev = min(impacts, key=RANK.get) if impacts else "INFO"
            issues.add(sev, [text(i.get("type")), code(i.get("rule")),
                             location(strip_component(i.get("component")), i.get("line")),
                             text(i.get("message"))])

    data = load_json(folder / "hotspots.json")
    if data is not None:
        hotspots.available = True
        for h in data:
            hotspots.add(normalize(h.get("vulnerabilityProbability")),
                         [text(h.get("securityCategory")), text(h.get("status")),
                          location(strip_component(h.get("component")), h.get("line")),
                          text(h.get("message"))])

    gate = load_json(folder / "quality-gate.json")
    measures = load_json(folder / "measures.json")
    notes = []
    status = (gate or {}).get("projectStatus", {}).get("status")
    if status:
        cls = "ok" if status == "OK" else "bad"
        notes.append(f'Quality gate: <span class="gate {cls}">{html.escape(status)}</span>')
    for m in (measures or {}).get("component", {}).get("measures", []):
        notes.append(f'{html.escape(str(m.get("metric", "")))}: <b>{html.escape(str(m.get("value", "")))}</b>')
    if notes:
        issues.notes.append(" · ".join(notes))
    return [issues, hotspots]


def parse_semgrep(folder):
    sec = Section("semgrep", "Semgrep – Code security (SAST)",
                  "Security bugs in the source code (Java, OWASP Top 10, secrets rule sets).",
                  ["Severity", "Rule", "Location", "Message", "CWE"])
    data = load_json(folder / "semgrep.json")
    if data is None:
        return [sec]
    sec.available = True
    for r in data.get("results", []):
        extra = r.get("extra", {})
        cwe = extra.get("metadata", {}).get("cwe", "")
        if isinstance(cwe, list):
            cwe = ", ".join(map(str, cwe))
        rule = str(r.get("check_id", ""))
        sec.add(normalize(extra.get("severity"), SEMGREP_SEVERITY),
                [code(rule.split(".")[-1] if rule else ""),
                 location(r.get("path"), r.get("start", {}).get("line")),
                 text(extra.get("message")), text(cwe)])
    errors = data.get("errors") or []
    if errors:
        sec.notes.append(f"Semgrep reported {len(errors)} scan error(s) (files it could not parse).")
    return [sec]


def parse_trivy(folder):
    sec = Section("trivy", "Trivy – Dependency vulnerabilities (SCA)",
                  "Known CVEs in Maven dependencies.",
                  ["Severity", "Vulnerability", "Package", "Installed", "Fixed in", "File", "Title"])
    data = load_json(folder / "trivy.json")
    if data is None:
        return [sec]
    sec.available = True
    for result in data.get("Results") or []:
        target = result.get("Target")
        for v in result.get("Vulnerabilities") or []:
            sec.add(normalize(v.get("Severity")),
                    [link(v.get("VulnerabilityID"), v.get("PrimaryURL")), code(v.get("PkgName")),
                     text(v.get("InstalledVersion")), text(v.get("FixedVersion") or "-"),
                     code(target), text(v.get("Title"))])
    return [sec]


def parse_kics(folder):
    sec = Section("kics", "KICS – Infrastructure as code",
                  "Misconfigurations in Dockerfiles, docker-compose and Terraform files.",
                  ["Severity", "Issue", "Platform", "Location", "Expected", "Actual"])
    data = load_json(folder / "results.json")
    if data is None:
        return [sec]
    sec.available = True
    for q in data.get("queries") or []:
        for f in q.get("files") or []:
            path = str(f.get("file_name", ""))
            if path.startswith("/src/"):
                path = path[len("/src/"):]
            sec.add(normalize(q.get("severity"), {"TRACE": "INFO"}),
                    [link(q.get("query_name"), q.get("query_url")), text(q.get("platform")),
                     location(path, f.get("line")), text(f.get("expected_value")),
                     text(f.get("actual_value"))])
    return [sec]


# ---- html --------------------------------------------------------------------

CSS = """
:root{--bg:#f6f7f9;--panel:#fff;--text:#1d2330;--muted:#5d6676;--border:#dde1e7;--head:#eef1f5;
--critical:#b0182a;--high:#d9480f;--medium:#b7791f;--low:#2b6cb0;--info:#5d6676;--ok:#2f855a}
@media (prefers-color-scheme: dark){:root{--bg:#14171c;--panel:#1c2027;--text:#e4e7ec;--muted:#9aa3b2;
--border:#323843;--head:#242a33;--critical:#f2546a;--high:#f08a4b;--medium:#e3b341;--low:#6aa9f0;--info:#9aa3b2;--ok:#56c28a}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--text);font:14px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:1400px;margin:0 auto;padding:24px 16px 64px}
h1{font-size:22px;margin:0 0 4px}
.meta{color:var(--muted);margin-bottom:20px}
.cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:12px;margin-bottom:20px}
.card{background:var(--panel);border:1px solid var(--border);border-radius:8px;padding:12px 14px;text-decoration:none;color:inherit}
.card:hover{border-color:var(--muted)}
.card .name{font-weight:600;margin-bottom:6px}
.card .total{font-size:24px;font-weight:700}
.badges{display:flex;flex-wrap:wrap;gap:4px}
.badge{display:inline-block;padding:1px 7px;border-radius:10px;font-size:12px;font-weight:600;color:#fff;white-space:nowrap}
.sev-CRITICAL{background:var(--critical)}.sev-HIGH{background:var(--high)}.sev-MEDIUM{background:var(--medium)}
.sev-LOW{background:var(--low)}.sev-INFO{background:var(--info)}
.filters{position:sticky;top:0;z-index:2;background:var(--bg);padding:10px 0;margin-bottom:12px;
display:flex;flex-wrap:wrap;gap:10px 16px;align-items:center;border-bottom:1px solid var(--border)}
.filters input[type=search]{flex:1 1 260px;min-width:0;padding:7px 10px;border:1px solid var(--border);border-radius:6px;
background:var(--panel);color:var(--text);font:inherit}
.filters select,.filters button{padding:6px 10px;border:1px solid var(--border);border-radius:6px;background:var(--panel);color:var(--text);font:inherit;cursor:pointer}
.sev-filter{display:flex;flex-wrap:wrap;gap:8px}
.sev-filter label{display:flex;align-items:center;gap:4px;cursor:pointer}
section.scan{margin-bottom:12px}
details{background:var(--panel);border:1px solid var(--border);border-radius:8px}
summary{cursor:pointer;padding:12px 14px;display:flex;flex-wrap:wrap;gap:8px 14px;align-items:center;list-style:none}
summary::-webkit-details-marker{display:none}
summary::before{content:"\\25B8";color:var(--muted);transition:transform .15s}
details[open] summary::before{transform:rotate(90deg)}
summary h2{font-size:16px;margin:0}
summary .shown{color:var(--muted);margin-left:auto}
.body{padding:0 14px 14px}
.desc,.note{color:var(--muted);margin:0 0 8px}
.missing{color:var(--high);font-weight:600}
.gate{font-weight:700}.gate.ok{color:var(--ok)}.gate.bad{color:var(--critical)}
.table-wrap{overflow-x:auto;border:1px solid var(--border);border-radius:6px}
table{border-collapse:collapse;width:100%}
th,td{text-align:left;padding:6px 10px;border-bottom:1px solid var(--border);vertical-align:top}
th{background:var(--head);position:sticky;top:0;font-weight:600;white-space:nowrap}
tr:last-child td{border-bottom:none}
td code{font-size:12px;word-break:break-all}
a{color:var(--low)}
.empty{padding:12px;color:var(--muted)}
"""

JS = """
const q = document.getElementById('q');
const scanSel = document.getElementById('scan');
const sevBoxes = [...document.querySelectorAll('.sev-filter input')];
const sections = [...document.querySelectorAll('section.scan')].map(sec => ({
  sec,
  details: sec.querySelector('details'),
  shown: sec.querySelector('.shown-n'),
  empty: sec.querySelector('.no-match'),
  rows: [...sec.querySelectorAll('tbody tr')].map(tr => ({tr, sev: tr.dataset.sev, text: tr.textContent.toLowerCase()}))
}));
function apply() {
  const terms = q.value.trim().toLowerCase().split(/\\s+/).filter(Boolean);
  const sevs = new Set(sevBoxes.filter(b => b.checked).map(b => b.value));
  const scan = scanSel.value;
  for (const s of sections) {
    s.sec.hidden = scan !== '' && s.sec.dataset.scan !== scan;
    let n = 0;
    for (const r of s.rows) {
      const ok = sevs.has(r.sev) && terms.every(t => r.text.includes(t));
      r.tr.hidden = !ok;
      if (ok) n++;
    }
    if (s.shown) s.shown.textContent = n;
    if (s.empty) s.empty.hidden = n > 0 || s.rows.length === 0;
    if (terms.length && n > 0) s.details.open = true;
  }
}
q.addEventListener('input', apply);
scanSel.addEventListener('change', apply);
sevBoxes.forEach(b => b.addEventListener('change', apply));
document.getElementById('reset').addEventListener('click', () => {
  q.value = ''; scanSel.value = ''; sevBoxes.forEach(b => b.checked = true); apply();
});
document.getElementById('expand').addEventListener('click', () => sections.forEach(s => s.details.open = true));
document.getElementById('collapse').addEventListener('click', () => sections.forEach(s => s.details.open = false));
document.querySelectorAll('.card').forEach(c => c.addEventListener('click', () => {
  const s = sections.find(s => s.sec.id === c.getAttribute('href').slice(1));
  if (s) { s.sec.hidden = false; s.details.open = true; }
}));
"""


def badges(counts, keep_zero=False):
    return "".join(f'<span class="badge sev-{s}">{s} {n}</span>'
                   for s, n in counts.items() if n or keep_zero)


def render_section(sec):
    counts = sec.counts()
    total = len(sec.rows)
    out = [f'<section class="scan" id="{sec.key}" data-scan="{sec.key}"><details>',
           f'<summary><h2>{html.escape(sec.title)}</h2><span class="badges">{badges(counts)}</span>']
    if sec.available:
        out.append(f'<span class="shown"><span class="shown-n">{total}</span> of {total} shown</span>')
    out.append('</summary><div class="body">')
    out.append(f'<p class="desc">{html.escape(sec.description)}</p>')
    for note in sec.notes:
        out.append(f'<p class="note">{note}</p>')

    if not sec.available:
        out.append('<p class="missing">No results found. The scan job may have failed or been skipped.</p>')
    elif not sec.rows:
        out.append('<p class="empty">No findings. 🎉</p>')
    else:
        out.append('<div class="table-wrap"><table><thead><tr>')
        out.extend(f"<th>{html.escape(c)}</th>" for c in sec.columns)
        out.append("</tr></thead><tbody>")
        for sev, cells in sorted(sec.rows, key=lambda r: RANK[r[0]]):
            out.append(f'<tr data-sev="{sev}"><td><span class="badge sev-{sev}">{sev}</span></td>')
            out.extend(f"<td>{h}</td>" for _, h in cells)
            out.append("</tr>")
        out.append("</tbody></table></div>")
        out.append('<p class="empty no-match" hidden>No findings match the current filter.</p>')
    out.append("</div></details></section>")
    return "".join(out)


def render(sections, title, subtitle):
    total = {s: 0 for s in SEVERITIES}
    for sec in sections:
        for s, n in sec.counts().items():
            total[s] += n

    cards = []
    for sec in sections:
        value = str(len(sec.rows)) if sec.available else "–"
        cards.append(f'<a class="card" href="#{sec.key}"><div class="name">{html.escape(sec.title)}</div>'
                     f'<div class="total">{value}</div><div class="badges">{badges(sec.counts())}</div></a>')

    options = "".join(f'<option value="{s.key}">{html.escape(s.title)}</option>' for s in sections)
    sev_boxes = "".join(f'<label><input type="checkbox" value="{s}" checked>'
                        f'<span class="badge sev-{s}">{s}</span></label>' for s in SEVERITIES)

    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<style>{CSS}</style>
</head>
<body>
<main>
<h1>{html.escape(title)}</h1>
<div class="meta">{subtitle} · {sum(total.values())} findings <span class="badges" style="display:inline-flex;vertical-align:middle">{badges(total)}</span></div>
<div class="cards">{''.join(cards)}</div>
<div class="filters">
  <input type="search" id="q" placeholder="Filter by text: file, rule, CVE, package, message…" aria-label="Filter by text">
  <select id="scan" aria-label="Scan"><option value="">All scans</option>{options}</select>
  <div class="sev-filter">{sev_boxes}</div>
  <button id="reset" type="button">Reset</button>
  <button id="expand" type="button">Expand all</button>
  <button id="collapse" type="button">Collapse all</button>
</div>
{''.join(render_section(s) for s in sections)}
</main>
<script>{JS}</script>
</body>
</html>
"""


def write_step_summary(sections, report_name):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not path:
        return
    lines = ["## Security report", "",
             "| Scan | " + " | ".join(SEVERITIES) + " | Total |",
             "|---|" + "---|" * (len(SEVERITIES) + 1)]
    for sec in sections:
        if sec.available:
            c = sec.counts()
            lines.append(f"| {sec.title} | " + " | ".join(str(c[s]) for s in SEVERITIES) + f" | {len(sec.rows)} |")
        else:
            lines.append(f"| {sec.title} | " + " | ".join("–" for _ in SEVERITIES) + " | no results |")
    lines += ["", f"Full HTML report: download the **{report_name}** artifact.", ""]
    with open(path, "a", encoding="utf-8") as f:
        f.write("\n".join(lines))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("results_dir", type=Path)
    ap.add_argument("output", type=Path)
    ap.add_argument("--title", default="Security scan report")
    ap.add_argument("--artifact-name", default="security-report")
    args = ap.parse_args()

    d = args.results_dir
    sections = (parse_sonar(d / "sonarqube-results") + parse_semgrep(d / "semgrep-results")
                + parse_trivy(d / "trivy-results") + parse_kics(d / "kics-results"))

    when = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    parts = [f"Generated {when}"]
    if os.environ.get("GITHUB_REPOSITORY"):
        repo = os.environ["GITHUB_REPOSITORY"]
        server = os.environ.get("GITHUB_SERVER_URL", "https://github.com")
        run_id = os.environ.get("GITHUB_RUN_ID", "")
        ref = os.environ.get("GITHUB_HEAD_REF") or os.environ.get("GITHUB_REF_NAME", "")
        sha = os.environ.get("GITHUB_SHA", "")[:7]
        parts.append(html.escape(f"{repo} @ {ref} ({sha})"))
        if run_id:
            url = html.escape(f"{server}/{repo}/actions/runs/{run_id}", quote=True)
            parts.append(f'<a href="{url}" target="_blank" rel="noopener">workflow run</a>')
    subtitle = " · ".join(parts)

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(render(sections, args.title, subtitle), encoding="utf-8")
    write_step_summary(sections, args.artifact_name)

    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(errors="replace")
    for sec in sections:
        status =f"{len(sec.rows)} findings" if sec.available else "no results"
        print(f"{sec.title}: {status}")
    print(f"Report written to {args.output}")


if __name__ == "__main__":
    main()
