import { resolve } from 'node:path'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      include: ['src/**/*.{ts,vue}'],
      // Presentational layer (views / components) is exercised
      // by Playwright e2e tests rather than vitest. Keeping
      // stores + services in scope preserves the 80% bar where
      // the logic actually lives.
      exclude: [
        'src/**/*.d.ts',
        'src/**/types/**',
        'src/main.ts',
        'src/router/**',
        // Layout shells are e2e territory — they render
        // RouterLinks and pass through slots; the testable bits
        // (route guards, nav active state) are exercised by
        // Playwright in services/system-tests.
        'src/layouts/**',
        'src/features/**/views/**',
        'src/features/**/components/**',
        'src/features/**/index.ts',
        // services/ is a thin pass-through to useApiWithAuth +
        // raw fetch (sessionSocket). Verifying it via vitest mocks
        // the same way the upstream helper already verifies itself,
        // so coverage there is double-counting. e2e tests in
        // playwright/ exercise the real HTTP path end-to-end.
        'src/features/**/services/**',
      ],
      thresholds: {
        lines: 80,
      },
    },
  },
})
