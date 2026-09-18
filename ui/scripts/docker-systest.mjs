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

// Defaults live in this process, not in the docker arguments, so they can be passed by name below.
process.env.POLARION_USER ??= 'admin';
process.env.POLARION_PASSWORD ??= 'admin';
process.env.POLARION_SYSTEST_PROJECT ??= 'elibrary';
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
// The bridge answers on the port of that URL, not always on 80: a browser writes the Host header
// from the URL it opens, so an instance on http://localhost:8080 has to find the bridge there.
const bridgePort = new URL(polarionUrl).port || (polarionUrl.startsWith('https:') ? '443' : '80');

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
  `BRIDGE_PORT=${bridgePort}`,
  // The names alone: docker takes the values from this process, so neither the password nor the
  // token reaches argv, where the process list of the machine would show them. The token is what
  // Polarion's REST API takes to prepare the work records.
  '-e',
  'POLARION_USER',
  '-e',
  'POLARION_PASSWORD',
  '-e',
  'POLARION_TOKEN',
  '-e',
  'POLARION_SYSTEST_PROJECT',
  '-v',
  `${uiDir}:/work`,
  '-v',
  '/work/node_modules',
  '-w',
  '/work',
  image,
  'bash',
  '-c',
  // The bridge runs in the background for as long as the suite does. The extra arguments are passed
  // as positional parameters: joined into the command they would lose their boundaries, so
  // --grep "two words" would arrive as two arguments.
  'npm ci && (node systest/host-bridge.mjs &) && sleep 2 && npx playwright test -c playwright.systest.config.js "$@"',
  'bash',
  ...extraArgs,
];

// Nothing secret is in the command any more: the values travel through the environment, so the
// call can be printed as it is.
console.log(`> docker ${args.join(' ')}`);
const result = spawnSync('docker', args, { stdio: 'inherit' });
if (result.error) {
  console.error(`Failed to launch docker: ${result.error.message}. Is Docker installed and running?`);
  process.exit(1);
}
process.exit(result.status ?? 1);
