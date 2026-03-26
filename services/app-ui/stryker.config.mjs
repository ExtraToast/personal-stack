/** @type {import('@stryker-mutator/api/core').PartialStrykerOptions} */
const config = {
  mutate: ['src/**/*.ts', '!src/**/*.test.ts', '!src/**/*.spec.ts', '!src/**/generated/**', '!src/**/*.d.ts'],
  testRunner: 'vitest',
  reporters: ['html', 'clear-text', 'progress'],
  coverageAnalysis: 'perTest',
  vitest: {
    configFile: 'vitest.config.ts',
  },
}

export default config
