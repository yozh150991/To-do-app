import { defineConfig, devices } from '@playwright/test';

// Serves the production web build with a plain static server: no COOP/COEP headers, exactly what
// a basic static host (e.g. GitHub Pages) provides. ADR-004 relies on that working.
const dist = process.env.WEB_DIST ?? '../build/dist/wasmJs/productionExecutable';
const port = 8090;

export default defineConfig({
  testDir: './tests',
  timeout: 180_000,
  retries: 0,
  workers: 1,
  reporter: [
    ['list'],
    ['junit', { outputFile: '../build/test-results/e2e/junit.xml' }],
    ['json', { outputFile: 'test-results/results.json' }],
  ],
  use: {
    baseURL: `http://127.0.0.1:${port}`,
    trace: 'retain-on-failure',
  },
  webServer: {
    command: `python3 -m http.server ${port} --bind 127.0.0.1 --directory ${dist}`,
    url: `http://127.0.0.1:${port}/index.html`,
    reuseExistingServer: false,
    timeout: 30_000,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    {
      name: 'firefox',
      use: {
        ...devices['Desktop Firefox'],
        // Compose needs WebGL 2. Headless Firefox has no GL driver on a GPU-less runner
        // ("Exhausted GL driver options"), so CI runs it headed under Xvfb with Mesa software GL
        // (FIREFOX_HEADED=1) and lifts the blocklist for software rendering.
        headless: process.env.FIREFOX_HEADED !== '1',
        launchOptions: { firefoxUserPrefs: { 'webgl.force-enabled': true } },
      },
    },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
  ],
});
