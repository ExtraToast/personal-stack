import antfu from '@antfu/eslint-config'

export default antfu(
  {
    vue: true,
    typescript: true,
    formatters: false,
  },
  {
    rules: {
      'no-console': 'warn',
      'ts/no-explicit-any': 'error',
      'ts/explicit-function-return-type': ['error', {
        allowExpressions: true,
        allowTypedFunctionExpressions: true,
        allowHigherOrderFunctions: true,
      }],
      'ts/consistent-type-assertions': ['warn', {
        assertionStyle: 'never',
      }],
    },
  },
  {
    files: ['**/*.vue'],
    rules: {
      'vue/define-emits-declaration': 'error',
      'vue/define-props-declaration': 'error',
      'vue/no-required-prop-with-default': 'error',
      'vue/require-prop-types': 'error',
      'vue/max-len': ['error', {
        code: 120,
        template: 120,
        ignoreUrls: true,
        ignoreStrings: true,
        ignoreTemplateLiterals: true,
      }],
    },
  },
  {
    files: ['**/*.test.ts', '**/*.spec.ts', '**/e2e/**'],
    rules: {
      'ts/no-explicit-any': 'off',
      'ts/explicit-function-return-type': 'off',
    },
  },
)
