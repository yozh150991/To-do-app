import { test, expect, type Page, type BrowserContext } from '@playwright/test';
import * as fs from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';

// Ф0 Web spike (spec §3.3, §3.3.1; ADR-004): Room + androidx WebWorkerSQLiteDriver + SQLite WASM
// with the OPFS SAHPool VFS, served without cross-origin isolation.

const evidenceDir = process.env.EVIDENCE_DIR ?? '../build/reports/runtime-evidence';

// Console noise that says nothing about the app:
// - Skiko probes the GPU name, WebKit/Firefox refuse;
// - Compose resources 1.12.2 reads resource bytes through `wasmExports.memory`, which the Kotlin 2.4
//   runtime reports as deprecated at console.error level (strings still load). Recheck on CMP updates.
const BENIGN_CONSOLE = [
  /WEBGL_debug_renderer_info not enabled/,
  /Accessing `memory` via `wasmExports` is deprecated/,
];

// Typed into the real UI, then read back from todo.db by ?selftest=inspect.
const UI_TASK = 'Typed in the web UI';

interface SelfTest {
  status: string;
  json: Record<string, unknown>;
}

function evidence(name: string, lines: string[]): void {
  fs.mkdirSync(evidenceDir, { recursive: true });
  fs.writeFileSync(path.join(evidenceDir, name), lines.join('\n') + '\n');
}

/**
 * Records everything the page says. kotlinx.coroutines reports uncaught coroutine exceptions with
 * reportError(), putting the full Kotlin stack trace (causes and suppressed included) into error.stack;
 * the init script prints it from inside the page, since not every browser hands it to Playwright intact.
 */
async function recordPage(page: Page, log: string[], errors: string[]): Promise<void> {
  await page.addInitScript(() => {
    window.addEventListener('error', (event) => {
      const error = (event as ErrorEvent).error;
      console.log(`[window error event] ${error?.name}: ${error?.message}\n${error?.stack ?? ''}`);
    });
    window.addEventListener('unhandledrejection', (event) => {
      const reason = (event as PromiseRejectionEvent).reason;
      console.log(`[unhandledrejection] ${reason?.name}: ${reason?.message}\n${reason?.stack ?? String(reason)}`);
    });
  });
  page.on('pageerror', (error) => {
    log.push(`[pageerror] ${error.name}: ${error.message}\n${error.stack ?? ''}`);
    errors.push(`${error.name}: ${error.message}`);
  });
  page.on('console', (message) => {
    const text = message.text();
    log.push(`[${message.type()}] ${text}`);
    if (message.type() === 'error' && !BENIGN_CONSOLE.some((pattern) => pattern.test(text))) errors.push(text);
  });
}

/** Production wasm frames carry no names; dropping them keeps causes and suppressed exceptions visible. */
function compact(entry: string): string {
  const lines = entry.split('\n');
  const kept = lines.filter((line) => !line.includes('wasm-function['));
  const dropped = lines.length - kept.length;
  return dropped > 0 ? `${kept.join('\n')}\n    (${dropped} unnamed wasm frames omitted)` : entry;
}

/** Errors first: the CI report keeps only the first 6000 characters of each evidence file. */
function pageLog(log: string[]): string[] {
  const isError = (line: string) => /^\[(pageerror|window error event|unhandledrejection|error)\]/.test(line);
  return [
    'page errors:', ...log.filter(isError).map((line) => `  ${compact(line)}`),
    'page log:', ...log.filter((line) => !isError(line)).map((line) => `  ${compact(line)}`),
  ];
}

async function selfTest(page: Page, query: string): Promise<SelfTest> {
  await page.goto(`/index.html?${query}`);
  const output = page.locator('#selftest[data-status]');
  await expect(output).toHaveAttribute('data-status', /^(pass|fail)$/, { timeout: 120_000 });
  const text = (await output.textContent()) ?? '{}';
  return { status: (await output.getAttribute('data-status')) ?? 'fail', json: JSON.parse(text) };
}

/** Leaves the page so its worker ends and the OPFS access handles are released. */
async function leave(page: Page): Promise<void> {
  await page.goto('about:blank');
  await page.waitForTimeout(500);
}

// Known limitation, kept visible: if WebKit starts passing, Playwright reports an unexpected pass.
const WEBKIT_EPHEMERAL =
  'WebKit gives no OPFS access handles to ephemeral (private-window-like) contexts; persistent profiles work (ADR-004)';

test('tasks persist in OPFS across reload and a new tab', async ({ page, context, browserName }) => {
  test.fail(browserName === 'webkit', WEBKIT_EPHEMERAL);
  const log: string[] = [];
  const errors: string[] = [];
  await recordPage(page, log, errors);
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
    await leave(page);
    const verify = await selfTest(page, `selftest=verify&workspace=${workspace}`);
    lines.push(`verify after reload: ${verify.status} ${JSON.stringify(verify.json)}`);
    expect(verify.status, JSON.stringify(verify.json)).toBe('pass');

    // Schema upgrade in the browser: a genuine v1 file opened by the v2 Room database.
    await leave(page);
    const migrate = await selfTest(page, 'selftest=migrate');
    lines.push(`migration v1 -> v2: ${migrate.status} ${JSON.stringify(migrate.json)}`);
    expect(migrate.status, JSON.stringify(migrate.json)).toBe('pass');

    // A new tab after the old one is closed takes over the database.
    await page.close();
    const reopened = await context.newPage();
    await recordPage(reopened, log, errors);
    const takeover = await selfTest(reopened, `selftest=verify&workspace=${workspace}`);
    lines.push(`new tab after closing the old one: ${takeover.status} ${JSON.stringify(takeover.json)}`);
    expect(takeover.status, JSON.stringify(takeover.json)).toBe('pass');

    // Quota and eviction (spec §3.3): storage stays best-effort until navigator.storage.persist().
    const storage = await reopened.evaluate(async () => {
      const estimate = await navigator.storage.estimate();
      return { persisted: await navigator.storage.persisted(), usage: estimate.usage, quota: estimate.quota };
    });
    lines.push(`storage: ${JSON.stringify(storage)}`);

    // Clearing site data removes the database; the next start must be clean, not broken.
    await leave(reopened);
    const clear = await selfTest(reopened, 'selftest=clear');
    expect(clear.status, JSON.stringify(clear.json)).toBe('pass');
    await leave(reopened);
    const fresh = await selfTest(reopened, 'selftest=write');
    lines.push(`write after clearing storage: ${fresh.status} new workspace: ${fresh.json.workspace !== workspace}`);
    expect(fresh.status, JSON.stringify(fresh.json)).toBe('pass');
    expect(fresh.json.workspace).not.toBe(workspace);
  } finally {
    lines.push(...pageLog(log));
    evidence(`web-${browserName}.txt`, lines);
  }
});

test('time zones and DST: golden fixtures TIME-01..04, TIME-08', async ({ page, browserName }) => {
  // Pure computation, no OPFS: runs in every browser, WebKit's ephemeral context included.
  const lines: string[] = [`browser: ${browserName}`];
  const log: string[] = [];
  const errors: string[] = [];
  await recordPage(page, log, errors);
  try {
    lines.push(`browser zone: ${await page.evaluate(() => Intl.DateTimeFormat().resolvedOptions().timeZone)}`);
    const time = await selfTest(page, 'selftest=time');
    lines.push(`status: ${time.status}`, ...((time.json.results as string[] | undefined) ?? [String(time.json.error)]));
    expect(time.status, JSON.stringify(time.json)).toBe('pass');
  } finally {
    lines.push(...pageLog(log));
    evidence(`time-${browserName}.txt`, lines);
  }
});

test('tasks persist after the browser is restarted', async ({ playwright, browserName }, testInfo) => {
  // A persistent profile on disk, closed and launched again: what a real browser restart does.
  // Also shows whether WebKit offers OPFS outside Playwright's ephemeral contexts.
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), `todo-profile-${browserName}-`));
  const baseURL = testInfo.project.use.baseURL ?? 'http://127.0.0.1:8090';
  const launch = () => playwright[browserName].launchPersistentContext(userDataDir, { baseURL });
  const lines: string[] = [`browser: ${browserName}`, `profile: ${userDataDir}`];
  const log: string[] = [];
  const errors: string[] = [];
  let context: BrowserContext | undefined;

  try {
    context = await launch();
    let page = context.pages()[0] ?? (await context.newPage());
    await recordPage(page, log, errors);
    const write = await selfTest(page, 'selftest=write');
    lines.push(`write: ${write.status} ${JSON.stringify(write.json)}`);
    expect(write.status, JSON.stringify(write.json)).toBe('pass');
    await context.close();
    context = undefined;

    context = await launch();
    page = context.pages()[0] ?? (await context.newPage());
    await recordPage(page, log, errors);
    const verify = await selfTest(page, `selftest=verify&workspace=${String(write.json.workspace)}`);
    lines.push(`verify after restart: ${verify.status} ${JSON.stringify(verify.json)}`);
    expect(verify.status, JSON.stringify(verify.json)).toBe('pass');
  } finally {
    await context?.close();
    fs.rmSync(userDataDir, { recursive: true, force: true });
    lines.push(...pageLog(log));
    evidence(`restart-${browserName}.txt`, lines);
  }
});

test('app UI starts, renders and saves a typed task', async ({ page, browserName }) => {
  test.fail(browserName === 'webkit', WEBKIT_EPHEMERAL);
  const log: string[] = [];
  const errors: string[] = [];
  const lines: string[] = [`browser: ${browserName}`];
  await recordPage(page, log, errors);
  // Localized strings come from composeResources/*.cvr files next to the page.
  const resources: string[] = [];
  page.on('response', (response) => {
    if (response.url().includes('composeResources')) {
      resources.push(`${response.status()} ${new URL(response.url()).pathname}`);
    }
  });

  try {
    // Compose for Web draws through WebGL 2 (Skiko); headless browsers without a GPU may lack it.
    const webgl = await page.evaluate(() => {
      const gl = document.createElement('canvas').getContext('webgl2');
      return gl ? `${gl.getParameter(gl.VERSION)}; ${gl.getParameter(gl.RENDERER)}` : 'unavailable';
    });
    lines.push(`webgl2: ${webgl}`);

    await page.goto('/index.html');
    // Either Compose's canvas or the plain startup message (e.g. no WebGL 2) appears.
    await expect(page.locator('canvas, #startup-error').first()).toBeVisible({ timeout: 120_000 });
    const startupError = page.locator('#startup-error');
    if ((await startupError.count()) > 0) lines.push(`startup message: ${await startupError.textContent()}`);
    expect(await startupError.count(), 'the app showed its startup error instead of the UI').toBe(0);
    await page.waitForTimeout(3_000);
    fs.mkdirSync(evidenceDir, { recursive: true });
    await page.screenshot({ path: path.join(evidenceDir, `ui-${browserName}.png`) });
    lines.push(`errors after start: ${JSON.stringify(errors)}`);
    lines.push('compose resources:', ...resources.map((line) => `  ${line}`));
    expect(resources.length, 'no composeResources requests').toBeGreaterThan(0);
    expect(resources.filter((line) => !line.startsWith('200')), 'failed composeResources requests').toEqual([]);

    // The new-task field sits under the 64dp top bar, 16dp from the left edge (InboxScreen).
    await page.mouse.click(200, 92);
    await page.keyboard.type(UI_TASK);
    await page.keyboard.press('Enter');
    await page.waitForTimeout(2_000);
    await page.screenshot({ path: path.join(evidenceDir, `ui-${browserName}-typed.png`) });

    await leave(page);
    const inspect = await selfTest(page, 'selftest=inspect');
    lines.push(`todo.db after typing: ${inspect.status} ${JSON.stringify(inspect.json)}`);
    expect(errors, errors.join('\n')).toEqual([]);
    expect(inspect.status, JSON.stringify(inspect.json)).toBe('pass');
    expect(inspect.json.open, JSON.stringify(inspect.json)).toEqual([UI_TASK]);
  } finally {
    lines.push(...pageLog(log));
    evidence(`ui-${browserName}.txt`, lines);
  }
});
