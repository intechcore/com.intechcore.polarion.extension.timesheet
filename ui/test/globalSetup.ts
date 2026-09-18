import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import type { TestProject } from 'vitest/node';

// The widget's height listener lives in the Java resources (src/main/resources/js/widget-height.js),
// which is outside this app's Vite root, so a test cannot import it. This setup runs in Node, reads
// the file and hands its text to the browser tests, which keeps that file the single source: the
// bundle Polarion serves and the suite that drives it read the same bytes.
export default function setup(project: TestProject): void {
  const script = resolve(import.meta.dirname, '../../src/main/resources/js/widget-height.js');
  project.provide('widgetHeightScript', readFileSync(script, 'utf8'));
}
