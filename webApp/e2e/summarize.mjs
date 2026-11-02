// Turns Playwright's JSON report into the Web verdict for CI (ADR-004, TECH_COMPATIBILITY).
// Chromium and Firefox are required (spec §3.3); WebKit is recorded but does not fail the job.
// Writes ../../web-result.txt (0 = pass) and a summary into the runtime evidence directory.
import * as fs from 'node:fs';
import * as path from 'node:path';

const REQUIRED = ['chromium', 'firefox'];
const resultsFile = 'test-results/results.json';
const evidenceDir = process.env.EVIDENCE_DIR ?? '../build/reports/runtime-evidence';
const resultFile = process.env.WEB_RESULT_FILE ?? '../../web-result.txt';

const byProject = {};
if (fs.existsSync(resultsFile)) {
  const report = JSON.parse(fs.readFileSync(resultsFile, 'utf8'));
  const visit = (suite) => {
    for (const spec of suite.specs ?? []) {
      for (const t of spec.tests ?? []) {
        const project = t.projectName;
        // test.fail() marks a known limitation: an expected failure counts as a pass of the check.
        const outcome = t.status === 'expected'
          ? (t.expectedStatus === 'failed' ? 'passed (known limitation, expected failure)' : 'passed')
          : (t.results?.at(-1)?.status ?? 'missing');
        (byProject[project] ??= []).push(`${outcome.padEnd(8)} ${spec.title}`);
      }
    }
    for (const child of suite.suites ?? []) visit(child);
  };
  for (const suite of report.suites ?? []) visit(suite);
}

const lines = [];
let ok = true;
for (const project of ['chromium', 'firefox', 'webkit']) {
  const tests = byProject[project] ?? [];
  const passed = tests.length > 0 && tests.every((line) => line.startsWith('passed'));
  const required = REQUIRED.includes(project);
  if (required && !passed) ok = false;
  const verdict = passed ? 'pass' : required ? 'FAIL' : 'not passing (recorded, not required in Ф0)';
  lines.push(`${project}: ${verdict}`);
  for (const line of tests) lines.push(`  ${line}`);
  if (tests.length === 0) lines.push('  no results');
}

fs.mkdirSync(evidenceDir, { recursive: true });
fs.writeFileSync(path.join(evidenceDir, 'web-summary.txt'), lines.join('\n') + '\n');
fs.writeFileSync(resultFile, ok ? '0\n' : '1\n');
console.log(lines.join('\n'));
