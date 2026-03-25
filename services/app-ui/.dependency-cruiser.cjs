/** @type {import('dependency-cruiser').IConfiguration} */
module.exports = {
  forbidden: [
    {
      name: 'no-circular',
      severity: 'error',
      comment: 'No circular imports allowed (ADR-013)',
      from: {},
      to: {
        circular: true,
      },
    },
    {
      name: 'no-components-import-views',
      severity: 'error',
      comment: 'Components must not import from views (ADR-013)',
      from: {
        path: '^src/features/.*/components/',
      },
      to: {
        path: '^src/features/.*/views/',
      },
    },
    {
      name: 'api-calls-only-from-services',
      severity: 'error',
      comment: 'API calls only from services layer (ADR-013)',
      from: {
        path: '^src/features/.*/(?!services/)',
        pathNot: '^src/shared/services/',
      },
      to: {
        path: '^src/shared/services/api/',
      },
    },
    {
      name: 'stores-through-services',
      severity: 'error',
      comment: 'Stores must not directly call generated API clients (ADR-013)',
      from: {
        path: '^src/features/.*/stores/',
      },
      to: {
        path: '^src/shared/services/api/generated/',
      },
    },
    {
      name: 'no-cross-feature-deep-import',
      severity: 'error',
      comment: 'Features must use barrel exports for cross-feature access (ADR-013)',
      from: {
        path: '^src/features/([^/]+)/',
      },
      to: {
        path: '^src/features/(?!$1/)[^/]+/(?!index\\.ts$)',
      },
    },
    {
      name: 'generated-client-isolation',
      severity: 'error',
      comment: 'Only service adapters may import generated API clients (ADR-016)',
      from: {
        pathNot: '^src/(features/.*/services/|shared/services/)',
      },
      to: {
        path: '^src/shared/services/api/generated/',
      },
    },
    {
      name: 'shared-components-domain-agnostic',
      severity: 'error',
      comment: 'Shared components must not import from features (ADR-013)',
      from: {
        path: '^src/shared/components/',
      },
      to: {
        path: '^src/features/',
      },
    },
  ],
  options: {
    doNotFollow: {
      path: 'node_modules',
    },
    tsPreCompilationDeps: true,
    tsConfig: {
      fileName: './tsconfig.json',
    },
    enhancedResolveOptions: {
      exportsFields: ['exports'],
      conditionNames: ['import', 'require', 'node', 'default'],
    },
    reporterOptions: {
      dot: {
        collapsePattern: 'node_modules/(@[^/]+/[^/]+|[^/]+)',
      },
    },
  },
}
