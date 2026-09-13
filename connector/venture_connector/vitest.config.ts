import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    globals: false,
    environment: 'node',
    include: ['test/**/*.test.ts'],
    globalSetup: ['test/helpers/global-teardown.ts'],
    pool: 'forks',
    server: {
      deps: {
        external: ['node:sqlite'],
      },
    },
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html'],
      include: ['src/**/*.ts'],
      exclude: ['src/**/*.d.ts'],
    },
  },
  ssr: {
    external: ['node:sqlite'],
  },
});
