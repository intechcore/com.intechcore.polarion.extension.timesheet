import react from '@vitejs/plugin-react';
import { createLogger, defineConfig, loadEnv } from 'vite';

// The app and react-sbb-polarion reference Polarion's own runtime assets by absolute path - the
// petrel theme CSS, generic's stylesheets, the Selawik fonts, the spinner image. None of them exist
// in this project, and all of them resolve once Polarion serves the bundle, which is exactly what
// Vite says before it leaves them alone. Silencing them keeps a genuinely missing local asset
// visible: the filter matches the /polarion/ prefix only.
function quietPolarionAssets() {
  const logger = createLogger();
  const { warn, warnOnce } = logger;
  const isPolarionAsset = (msg) => /(^|\s)\/polarion\//.test(msg);

  logger.warn = (msg, options) => {
    if (!isPolarionAsset(msg)) warn(msg, options);
  };
  logger.warnOnce = (msg, options) => {
    if (!isPolarionAsset(msg)) warnOnce(msg, options);
  };
  return logger;
}

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const polarionUrl = env.VITE_BASE_URL || 'http://localhost';

  // Dedupe so the app and react-sbb-polarion resolve to a single React instance (a second copy gives
  // the "invalid hook call" failure). sonner too: the app's `toast()` and RSP's `Toaster` host must
  // share one sonner instance.
  const resolve = { dedupe: ['react', 'react-dom', 'sonner'] };

  if (command === 'serve') {
    return {
      plugins: [react()],
      resolve,
      server: {
        proxy: {
          // The extension's own webapp context: its REST API, which the About page reads.
          '/polarion/timesheet/rest': {
            target: polarionUrl,
            changeOrigin: true,
          },
          '/polarion/rest': {
            target: polarionUrl,
            changeOrigin: true,
          },
          '/polarion/ria': {
            target: polarionUrl,
            changeOrigin: true,
          },
          '/polarion/icons': {
            target: polarionUrl,
            changeOrigin: true,
          },
        },
      },
    };
  }

  return {
    plugins: [react()],
    resolve,
    // Never let a developer's personal access token reach a shipped bundle. VITE_BEARER_TOKEN is a
    // `vite dev` convenience (it switches useRemote to the token-authenticated /api endpoints); Vite
    // inlines import.meta.env.VITE_* at build time, so a local .env.local would otherwise be baked
    // into the bundle that `mvn -P install-to-local-polarion` deploys, readable by everyone the SPA is
    // served to. Forcing it undefined here keeps production on the session-authenticated /internal
    // endpoints, which is what Polarion provides anyway.
    define: { 'import.meta.env.VITE_BEARER_TOKEN': 'undefined' },
    base: '/polarion/timesheet-app/ui/app/',
    customLogger: quietPolarionAssets(),
    build: {
      outDir: './dist/app',
      emptyOutDir: true,
    },
  };
});
