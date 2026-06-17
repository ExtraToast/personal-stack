import test from 'node:test'
import assert from 'node:assert/strict'
import { runProcess } from './_helpers.js'

// Regression for the EPIPE flake in runProcess: a child that closes its stdin
// before the parent finishes writing the payload must not crash the call with
// "Error: write EPIPE". The helper should swallow the stdin EPIPE and resolve
// with the child's real exit code. See platform/tests/_helpers.js::runProcess.
//
// The silent-hooks test in agent-kit-manifest.test.js exercises this with 16
// input-bearing hooks that exit before reading stdin; this reproduces the race
// deterministically and in isolation.
test('runProcess tolerates a child that closes stdin before reading input', async () => {
  // Node child: close fd 0 immediately, stay alive briefly so the parent's
  // large write races the now-closed pipe, then exit with a nonzero code.
  const script = 'process.stdin.destroy(); setTimeout(() => process.exit(7), 50)'
  // Payload well beyond the OS pipe buffer (~64 KiB) so the write cannot
  // complete in one shot and is guaranteed to hit the closed read end.
  const input = Buffer.alloc(2 * 1024 * 1024, 'x').toString('utf8')

  const result = await runProcess(process.execPath, ['-e', script], { input })

  // Resolves (not rejects) and preserves the real nonzero exit status.
  assert.equal(result.exitCode, 7)
})
