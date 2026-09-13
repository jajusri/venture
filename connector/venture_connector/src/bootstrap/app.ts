import type { ApplicationContext } from './register-services.js';
import { registerServices, startApplication, stopApplication } from './register-services.js';

export interface ShutdownOptions {
  readonly timeoutMs: number;
}

export function createShutdownHandler(context: ApplicationContext, options: ShutdownOptions) {
  let shuttingDown = false;

  return async (signal: string): Promise<void> => {
    if (shuttingDown) return;
    shuttingDown = true;

    context.logger.info('Shutdown signal received', { signal });

    const forceExitTimer = setTimeout(() => {
      context.logger.error('Graceful shutdown timed out; forcing exit', {
        timeoutMs: options.timeoutMs,
      });
      process.exit(1);
    }, options.timeoutMs);

    try {
      await stopApplication(context);
      clearTimeout(forceExitTimer);
      context.logger.info('Graceful shutdown complete');
      process.exit(0);
    } catch (error) {
      clearTimeout(forceExitTimer);
      context.logger.error('Graceful shutdown failed', {
        error: error instanceof Error ? error.message : String(error),
      });
      process.exit(1);
    }
  };
}

export async function bootstrap(): Promise<ApplicationContext> {
  const context = registerServices();
  context.logger.info('Venture connector bootstrapping', {
    version: context.config.connectorVersion,
    schemaVersion: context.config.schemaVersion,
    env: context.config.env,
  });

  await startApplication(context);

  const shutdown = createShutdownHandler(context, {
    timeoutMs: context.config.gracefulShutdownMs,
  });

  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('SIGTERM', () => void shutdown('SIGTERM'));

  return context;
}

export { registerServices, startApplication, stopApplication };
