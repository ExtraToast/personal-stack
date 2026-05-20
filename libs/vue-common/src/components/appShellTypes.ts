/**
 * Public types for the AppShell component. Lives in its own module
 * so the type can be re-exported from the package barrel without
 * Vue SFC quirks.
 */
export interface AppShellNavItem {
  /** Visible label. */
  label: string
  /**
   * Target route — either a path string or a route-location object
   * the way `<RouterLink :to>` accepts.
   */
  to: string | { path: string; hash?: string }
  /** data-testid for Playwright wiring. */
  testid?: string
  /** Optional active predicate; falls back to a path-prefix match. */
  active?: (currentPath: string) => boolean
}
