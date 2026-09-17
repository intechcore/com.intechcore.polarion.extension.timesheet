/**
 * True only inside the pinned Playwright Docker image, where the committed reference screenshots were
 * generated (scripts/docker-test.mjs sets PIXEL_REFERENCES=1; vitest.config.ts turns it into this
 * compile-time constant). Visual suites gate themselves on it with `describe.skipIf(!__PIXEL_REFERENCES__)`
 * so a run on any other host reports the behavior results instead of failing on font metrics.
 */
declare const __PIXEL_REFERENCES__: boolean;

declare module 'vitest' {
  interface ProvidedContext {
    /**
     * The text of src/main/resources/js/widget-height.js, read in Node by test/globalSetup.ts. The
     * file sits outside the Vite root of this app, so a browser test receives it this way rather
     * than importing it.
     */
    widgetHeightScript: string;
  }
}
