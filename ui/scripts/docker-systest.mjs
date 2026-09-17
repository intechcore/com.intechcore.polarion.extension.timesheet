// Runs the system suite inside the pinned Playwright Docker image, so the screenshots match the
// committed references. The suite talks to a Polarion running on the host: inside the container that
// is host.docker.internal, which Docker Desktop resolves, and which --add-host provides elsewhere.
//
//   npm run systest:docker           assert against the references
//   npm run systest:update:docker    rewrite them
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const uiDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const extraArgs = process.argv.slice(2);

let playwrightVersion;
try {
  const pkg = JSON.parse(readFileSync(resolve(uiDir, 'node_modules/playwright/package.json'), 'utf8'));
  playwrightVersion = pkg.version;
} catch {
  console.error('Cannot read node_modules/playwright - run `npm install` first.');
  process.exit(1);
}
const image = `mcr.microsoft.com/playwright:v${playwrightVersion}-noble`;

// Inside the container Polarion is reached as localhost, through systest/host-bridge.mjs. It
// answers only requests whose Host header matches its base.url, and a browser writes that header
// from the URL it opens.
const polarionUrl = process.env.POLARION_URL || 'http://localhost';

const args = [
  'run',
  '--rm',
  '--shm-size=1g',
  '--add-host',
  'host.docker.internal:host-gateway',
  '-e',
  'PIXEL_REFERENCES=1',
  '-e',
  `POLARION_URL=${polarionUrl}`,
  '-e',
  `POLARION_USER=${process.env.POLARION_USER || 'admin'}`,
  '-e',
  `POLARION_PASSWORD=${process.env.POLARION_PASSWORD || 'admin'}`,
  // Preparing the work records goes through Polarion's REST API, which takes a token and nothing else.
  '-e',
  `POLARION_TOKEN=${process.env.POLARION_TOKEN || ''}`,
  '-e',
  `POLARION_SYSTEST_PROJECT=${process.env.POLARION_SYSTEST_PROJECT || 'elibrary'}`,
  '-v',
  `${uiDir}:/work`,
  '-v',
  '/work/node_modules',
  '-w',
  '/work',
  image,
  'bash',
  '-c',
  // The bridge runs in the background for as long as the suite does.
  `npm ci && (node systest/host-bridge.mjs &) && sleep 2 && npx playwright test -c playwright.systest.config.js ${extraArgs.join(' ')}`,
];

console.log(`> docker ${args.join(' ')}`);
const result = spawnSync('docker', args, { stdio: 'inherit' });
if (result.error) {
  console.error(`Failed to launch docker: ${result.error.message}. Is Docker installed and running?`);
  process.exit(1);
}
process.exit(result.status ?? 1);
