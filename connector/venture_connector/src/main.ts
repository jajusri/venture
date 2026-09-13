import { bootstrap } from './bootstrap/app.js';

bootstrap().catch((error) => {
  console.error(
    JSON.stringify({
      timestamp: new Date().toISOString(),
      level: 'error',
      message: 'Fatal bootstrap error',
      error: error instanceof Error ? error.message : String(error),
    }),
  );
  process.exit(1);
});
