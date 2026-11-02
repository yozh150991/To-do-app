import { test, expect, type Page } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';

// Ф0 Web spike (spec §3.3, §3.3.1; ADR-004): Room + androidx WebWorkerSQLiteDriver + SQLite WASM
// with the OPFS SAHPool VFS, served without cross-origin isolation.

const evidenceDir = process.env.EVIDENCE_DIR ?? '../build/reports/runtime-evidence';

interface SelfTest {
  status: string;
  json: Record<string, unknown>;
}

function evidence(name: string, lines: string[]): void {
  fs.mkdirSync(evidenceDir, { recursive: true });
  fs.writeFileSync(path.join(evidenceDir, name), lines.join('\n') + '\n');
}

async function selfTest(page: Page, query: string): Promise<SelfTest> {
  await page.goto(`/index.html?${query}`);
  const output = page.locator('#selftest[data-status]');
  await expect(output).toHaveAttribute('data-status', /^(pass|fail)$/, { timeout: 120_000 });
  const text = (await output.textContent()) ?? '{}';
  return { status: (await output.getAttribute('data-status')) ?? 'fail', json: JSON.parse(text) };
}

test('tasks persist in OPFS across reload and a new tab', async ({ page, context, browserName }) => {
  const consoleLines: string[] = [];
  page.on('console', (message) => consoleLines.push(`[${message.type()}] ${message.text()}`));
  const lines: string[] = [`browser: ${browserName} ${context.browser()?.version() ?? ''}`];

  try {
    const write = await selfTest(page, 'selftest=write');
    lines.push(`crossOriginIsolated: ${await page.evaluate(() => (globalThis as any).crossOriginIsolated)}`);
    lines.push(`write: ${write.status} ${JSON.stringify(write.json)}`);
    expect(write.status, JSON.stringify(write.json)).toBe('pass');
    const workspace = String(write.json.workspace);

    // A second tab while the first one holds the OPFS handles: expected to fail with a clear error.
    const second = await context.newPage();
    const parallel = await selfTest(second, `selftest=verify&workspace=${workspace}`);
    lines.push(`second tab while the first is open: ${parallel.status} ${String(parallel.json.error ?? '')}`);
    await second.close();

    // The first tab navigates away and comes back: the data must still be there.
    await page.goto('about:blank');
    const verify = await selfTest(page, `selftest=verify&workspace=${workspace}`);
    lines.push(`verify after reload: ${verify.status} ${JSON.stringify(verify.json)}`);
    expect(verify.status, JSON.stringify(verify.json)).toBe('pass');

    // A new tab after the old one is closed takes over the database.
    await page.close();
    const reopened = await context.newPage();
    const takeover = await selfTest(reopened, `selftest=verify&workspace=${workspace}`);
    lines.push(`new tab after closing the old one: ${takeover.status} ${JSON.stringify(takeover.json)}`);
    expect(takeover.status, JSON.stringify(takeover.json)).toBe('pass');
  } finally {
    lines.push('console:', ...consoleLines.map((line) => `  ${line}`));
    evidence(`web-${browserName}.txt`, lines);
  }
});

test('app UI starts and renders', async ({ page, browserName }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(String(error)));
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text());
  });
  await page.goto('/index.html');
  await expect(page.locator('canvas').first()).toBeVisible({ timeout: 120_000 });
  await page.waitForTimeout(3_000);
  fs.mkdirSync(evidenceDir, { recursive: true });
  await page.screenshot({ path: path.join(evidenceDir, `ui-${browserName}.png`) });
  expect(errors, errors.join('\n')).toEqual([]);
});
