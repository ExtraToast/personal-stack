import test from 'node:test'
import assert from 'node:assert/strict'
import http from 'node:http'
import path from 'node:path'
import { existsSync } from 'node:fs'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import YAML from 'yaml'
import {
  assertContains,
  assertSameMembers,
  filesUnder,
  readRepoText,
  readYamlRepo,
  relativePath,
  repoPath,
  repoRoot,
  runProcess,
  sha256,
  tempDir,
  writeExecutable,
} from './_helpers.js'

const manifest = await readYamlRepo('platform/agents/kit/manifest.yaml')

test('manifest pins every checked-in skill hook setting and installer file', async () => {
  const actualRepoSkillPaths = await repoSkillPaths()
  const manifestSkillPaths = new Set(manifestTargetPaths('skills').filter((item) => item.includes('/skills/')))
  assertSameMembers(
    manifestSkillPaths,
    actualRepoSkillPaths,
    'every checked-in Claude/Codex skill must be listed in the agent-kit manifest',
  )

  const actualRepoHookPaths = await repoHookPaths()
  const manifestHookPaths = new Set(manifestTargetPaths('hooks').filter((item) => item.includes('/hooks/')))
  assertSameMembers(
    manifestHookPaths,
    actualRepoHookPaths,
    'every checked-in Claude/Codex hook must be listed in the agent-kit manifest',
  )

  const actualRepoCommandPaths = await repoCommandPaths()
  const manifestCommandPaths = new Set(manifestTargetPaths('commands').filter((item) => item.includes('/commands/')))
  assertSameMembers(
    manifestCommandPaths,
    actualRepoCommandPaths,
    'every checked-in Claude command must be listed in the agent-kit manifest',
  )

  const pinnedPaths = collectPinnedPaths(manifest)
  for (const required of [
    '.claude/settings.json',
    '.codex/hooks.json',
    'services/knowledge-api/src/main/resources/installer/install.sh',
  ]) {
    assert.ok(pinnedPaths.map((item) => item.path).includes(required))
  }
  for (const pinned of pinnedPaths) {
    const file = rendererSourcePath(pinned.path)
    assert.ok(existsSync(file), `manifest path exists: ${pinned.path}`)
    assert.equal(await sha256(file), pinned.sha256, `sha256 for ${pinned.path}`)
  }

  for (const command of manifestItems('commands')) {
    const name = command.name
    const expectedPath = `.claude/commands/${name}.md`
    assert.equal(command.installer.target_path, `\${CLAUDE_HOME}/commands/${name}.md`)
    assert.deepEqual(itemTargetPaths(command), [expectedPath])
    assert.equal(command.targets[0].sha256, await sha256(repoPath(expectedPath)))
  }
})

test('shared skills exist for both Claude and Codex unless a gap is explicit', async () => {
  const codexSkillNames = [...(await skillNamesUnder('.agents/skills'))].filter((name) => !name.startsWith('speckit-'))
  const claudeSkillNames = [...(await skillNamesUnder('.claude/skills'))].filter((name) => !name.startsWith('speckit-'))
  assertSameMembers(codexSkillNames, claudeSkillNames, 'repo-level Codex and Claude skill sets must stay in lockstep')

  for (const skill of manifestItems('skills').filter((item) => item.name.startsWith('speckit-'))) {
    assert.deepEqual(supportedAgents(skill), ['codex'])
    assert.ok(skill.unsupported?.claude?.trim())
  }
  for (const skill of manifestItems('skills')) assertAgentGapIsExplicit(`skill ${skill.name}`, skill)
  assertAgentGapIsExplicit('installer', manifest.installer)
})

test('Spec Kit Claude commands and Codex skills stay in one to one parity', async () => {
  const commandNames = new Set(
    [...(await repoCommandPaths())].map((item) =>
      item.substring(item.lastIndexOf('/speckit.') + 9).replace(/\.md$/, ''),
    ),
  )
  const skillNames = new Set(
    [...(await repoSkillPaths())]
      .map((item) => /^\.agents\/skills\/speckit-([^/]+)\/SKILL\.md$/.exec(item)?.[1])
      .filter(Boolean),
  )
  assertSameMembers(
    skillNames,
    commandNames,
    'each /speckit.<command> must have a matching Codex speckit-<command> skill',
  )
})

test('installer managed surfaces are listed in the manifest', async () => {
  const installer = await readRepoText('services/knowledge-api/src/main/resources/installer/install.sh')
  const installedSkillNames = new Set(
    [...installer.matchAll(/\$\{(?:CODEX_)?SKILLS_DIR}\/([^/]+)\/SKILL\.md/g)].map((match) => match[1]),
  )
  const manifestInstallerSkillNames = new Set(
    manifestItems('skills')
      .filter((item) => item.installer)
      .map((item) => item.name),
  )
  assertSameMembers(
    manifestInstallerSkillNames,
    installedSkillNames,
    'every installer-managed skill must be visible in the agent-kit manifest',
  )

  const installedHookNames = new Set(
    [...installer.matchAll(/\$\{(?:CODEX_)?HOOKS_DIR}\/([^"]+\.sh)/g)].map((match) => match[1]),
  )
  const manifestInstallerHookNames = new Set(
    manifestItems('hooks').flatMap((hook) =>
      [hook.installer?.target_path, hook.installer?.codex_target_path]
        .filter(Boolean)
        .map((item) => item.substring(item.lastIndexOf('/') + 1)),
    ),
  )
  assertSameMembers(
    manifestInstallerHookNames,
    installedHookNames,
    'every installer-managed hook must be visible in the agent-kit manifest',
  )
})

test('installer dry-run covers Claude and Codex managed surfaces', async () => {
  const dir = await tempDir()
  const installer = repoPath(manifest.installer.path)
  const claudeHome = path.join(dir, 'installer-claude')
  const codexHome = path.join(dir, 'installer-codex')
  const result = await runProcess('bash', [installer, '--agent', 'all', '--dry-run'], {
    env: { CLAUDE_CONFIG_DIR: claudeHome, CODEX_HOME: codexHome },
  })
  assert.equal(result.exitCode, 0, result.stderr)
  assertContains(
    result.stdout,
    `would write ${claudeHome}/hooks/user-prompt-submit-recall.sh`,
    `would write ${claudeHome}/hooks/pre-tool-use-edit-recall.sh`,
    `would write ${claudeHome}/hooks/pre-tool-use-git-commit-capture.sh`,
    `would write ${claudeHome}/hooks/stop-session-digest.sh`,
    `would write ${claudeHome}/commands/speckit.analyze.md`,
    `would write ${claudeHome}/commands/speckit.taskstoissues.md`,
    `would write ${claudeHome}/.knowledge-system-allowlist`,
    `would write ${codexHome}/hooks/kb-user-prompt-recall.sh`,
    `would write ${codexHome}/hooks/pre-tool-use-edit-recall.sh`,
    `would write ${codexHome}/hooks/pre-tool-use-git-commit-capture.sh`,
    `would write ${codexHome}/hooks/kb-stop-digest.sh`,
    `would write ${codexHome}/skills/speckit-analyze/SKILL.md`,
    `would write ${codexHome}/skills/speckit-taskstoissues/SKILL.md`,
    `would write ${codexHome}/hooks.json`,
    `${codexHome}/hooks.json has been written with UserPromptSubmit, PreToolUse,`,
  )
  assert.equal(existsSync(path.join(codexHome, 'hooks.json')), false)
})

test('installer agent selection covers Spec Kit commands and Codex skills', async () => {
  const dir = await tempDir()
  const installer = repoPath(manifest.installer.path)
  for (const agent of ['claude', 'codex', 'all']) {
    const claudeHome = path.join(dir, `agent-${agent}-claude`)
    const codexHome = path.join(dir, `agent-${agent}-codex`)
    const env = { CLAUDE_CONFIG_DIR: claudeHome, CODEX_HOME: codexHome }
    const dryRunResult = await runProcess('bash', [installer, '--agent', agent, '--dry-run'], { env })
    assert.equal(dryRunResult.exitCode, 0, dryRunResult.stderr)
    assert.equal(
      dryRunResult.stdout.includes(`would write ${claudeHome}/commands/speckit.analyze.md`),
      agent !== 'codex',
    )
    assert.equal(
      dryRunResult.stdout.includes(`would write ${codexHome}/skills/speckit-analyze/SKILL.md`),
      agent !== 'claude',
    )
    const installResult = await runProcess('bash', [installer, '--agent', agent], { env })
    assert.equal(installResult.exitCode, 0, installResult.stderr)
    const claudeCommand = path.join(claudeHome, 'commands/speckit.analyze.md')
    const codexSkill = path.join(codexHome, 'skills/speckit-analyze/SKILL.md')
    assert.equal(existsSync(claudeCommand), agent !== 'codex')
    assert.equal(existsSync(codexSkill), agent !== 'claude')
    const uninstallResult = await runProcess('bash', [installer, '--agent', agent, '--uninstall'], { env })
    assert.equal(uninstallResult.exitCode, 0, uninstallResult.stderr)
    assert.equal(existsSync(claudeCommand), false)
    assert.equal(existsSync(codexSkill), false)
  }
})

test('installer writes parseable Codex hooks and uninstalls managed files', async () => {
  const dir = await tempDir()
  const installer = repoPath(manifest.installer.path)
  const claudeHome = path.join(dir, 'write-claude')
  const codexHome = path.join(dir, 'write-codex')
  const env = { CLAUDE_CONFIG_DIR: claudeHome, CODEX_HOME: codexHome }
  const installResult = await runProcess('bash', [installer, '--agent', 'all'], { env })
  assert.equal(installResult.exitCode, 0, installResult.stderr)
  assertContains(installResult.stdout, 'knowledge-system installer complete', 'agent=all')

  const claudeFiles = [
    'hooks/user-prompt-submit-recall.sh',
    'hooks/pre-tool-use-edit-recall.sh',
    'hooks/pre-tool-use-git-commit-capture.sh',
    'hooks/stop-session-digest.sh',
    'commands/speckit.analyze.md',
    'commands/speckit.checklist.md',
    'commands/speckit.clarify.md',
    'commands/speckit.constitution.md',
    'commands/speckit.implement.md',
    'commands/speckit.plan.md',
    'commands/speckit.specify.md',
    'commands/speckit.tasks.md',
    'commands/speckit.taskstoissues.md',
    'skills/topics/SKILL.md',
    'skills/audit/SKILL.md',
    'skills/kb-first/SKILL.md',
    'skills/token-economy/SKILL.md',
    'skills/agent-session-bootstrap/SKILL.md',
    'skills/council/SKILL.md',
    'skills/council/council.py',
    'skills/council/council.toml',
    'skills/council/prompts/planner.md',
    'skills/council/schemas/plan.schema.json',
    '.knowledge-system-allowlist',
    '.knowledge-system-version',
  ].map((item) => path.join(claudeHome, item))
  const codexFiles = [
    'hooks/kb-user-prompt-recall.sh',
    'hooks/pre-tool-use-edit-recall.sh',
    'hooks/pre-tool-use-git-commit-capture.sh',
    'hooks/kb-stop-digest.sh',
    'skills/speckit-analyze/SKILL.md',
    'skills/speckit-checklist/SKILL.md',
    'skills/speckit-clarify/SKILL.md',
    'skills/speckit-constitution/SKILL.md',
    'skills/speckit-implement/SKILL.md',
    'skills/speckit-plan/SKILL.md',
    'skills/speckit-specify/SKILL.md',
    'skills/speckit-tasks/SKILL.md',
    'skills/speckit-taskstoissues/SKILL.md',
    'skills/topics/SKILL.md',
    'skills/audit/SKILL.md',
    'skills/kb-first/SKILL.md',
    'skills/token-economy/SKILL.md',
    'skills/agent-session-bootstrap/SKILL.md',
    'skills/council/SKILL.md',
    'skills/council/council.py',
    'skills/council/council.toml',
    'skills/council/prompts/planner.md',
    'skills/council/schemas/plan.schema.json',
    '.knowledge-system-allowlist',
    '.knowledge-system-version',
    'hooks.json',
  ].map((item) => path.join(codexHome, item))
  for (const file of [...claudeFiles, ...codexFiles]) assert.ok(existsSync(file), `installer wrote ${file}`)
  for (const file of [
    path.join(claudeHome, 'hooks/user-prompt-submit-recall.sh'),
    path.join(claudeHome, 'hooks/pre-tool-use-edit-recall.sh'),
    path.join(claudeHome, 'hooks/pre-tool-use-git-commit-capture.sh'),
    path.join(claudeHome, 'hooks/stop-session-digest.sh'),
    path.join(codexHome, 'hooks/kb-user-prompt-recall.sh'),
    path.join(codexHome, 'hooks/pre-tool-use-edit-recall.sh'),
    path.join(codexHome, 'hooks/pre-tool-use-git-commit-capture.sh'),
    path.join(codexHome, 'hooks/kb-stop-digest.sh'),
    path.join(claudeHome, 'skills/council/council.py'),
    path.join(codexHome, 'skills/council/council.py'),
  ])
    assert.ok((await import('node:fs')).statSync(file).mode & 0o111, `installer made hook executable: ${file}`)
  assertContains(
    await readFile(path.join(claudeHome, '.knowledge-system-version'), 'utf8'),
    'scope=user',
    'managed:',
    'hooks/user-prompt-submit-recall.sh',
    'commands/speckit.analyze.md',
  )
  assertContains(
    await readFile(path.join(codexHome, '.knowledge-system-version'), 'utf8'),
    'agent=codex',
    'scope=user',
    'hooks.json',
    'skills/speckit-analyze/SKILL.md',
  )
  assert.equal(existsSync(path.join(dir, 'write-claude/.specify')), false)
  assert.equal(existsSync(path.join(dir, 'write-codex/.specify')), false)
  const codexHooks = JSON.parse(await readFile(path.join(codexHome, 'hooks.json'), 'utf8'))
  assert.deepEqual(hookCommands(codexHooks, 'UserPromptSubmit'), [
    path.join(codexHome, 'hooks/kb-user-prompt-recall.sh'),
  ])
  assertSameMembers(hookMatchers(codexHooks, 'PreToolUse'), ['Edit|Write|apply_patch', 'Bash'])
  assertSameMembers(hookCommands(codexHooks, 'PreToolUse'), [
    `env KB_AUTO_MCP_HOME=${codexHome} ${codexHome}/hooks/pre-tool-use-edit-recall.sh`,
    `env KB_AUTO_MCP_HOME=${codexHome} KB_AUTO_MCP_SOURCE=codex:auto-capture:git-commit KB_AUTO_MCP_CLIENT_NAME=Codex ${codexHome}/hooks/pre-tool-use-git-commit-capture.sh`,
  ])
  assert.deepEqual(hookCommands(codexHooks, 'Stop'), [path.join(codexHome, 'hooks/kb-stop-digest.sh')])
  const uninstallResult = await runProcess('bash', [installer, '--agent', 'all', '--uninstall'], { env })
  assert.equal(uninstallResult.exitCode, 0, uninstallResult.stderr)
  for (const file of [...claudeFiles, ...codexFiles]) assert.equal(existsSync(file), false, `uninstall removed ${file}`)
})

test('installer project scope writes claude and codex files under project root', async () => {
  const dir = await tempDir()
  const installer = repoPath(manifest.installer.path)
  const projectRoot = path.join(dir, 'project-root')
  await mkdir(projectRoot, { recursive: true })
  const ignoredClaudeHome = path.join(dir, 'ignored-claude')
  const ignoredCodexHome = path.join(dir, 'ignored-codex')
  const env = {
    AGENT_KIT_PROJECT_ROOT: projectRoot,
    CLAUDE_CONFIG_DIR: ignoredClaudeHome,
    CODEX_HOME: ignoredCodexHome,
  }
  const installResult = await runProcess('bash', [installer, '--agent', 'all', '--scope', 'project'], { env })
  assert.equal(installResult.exitCode, 0, installResult.stderr)
  assertContains(installResult.stdout, 'knowledge-system installer complete', 'agent=all', 'scope=project')
  const claudeHome = path.join(projectRoot, '.claude')
  const codexHome = path.join(projectRoot, '.codex')
  const managedFiles = [
    path.join(claudeHome, 'hooks/user-prompt-submit-recall.sh'),
    path.join(claudeHome, 'hooks/pre-tool-use-edit-recall.sh'),
    path.join(claudeHome, 'hooks/pre-tool-use-git-commit-capture.sh'),
    path.join(claudeHome, 'hooks/stop-session-digest.sh'),
    path.join(claudeHome, 'commands/speckit.analyze.md'),
    path.join(claudeHome, 'commands/speckit.taskstoissues.md'),
    path.join(claudeHome, 'skills/kb-first/SKILL.md'),
    path.join(claudeHome, '.knowledge-system-allowlist'),
    path.join(claudeHome, '.knowledge-system-version'),
    path.join(codexHome, 'hooks/kb-user-prompt-recall.sh'),
    path.join(codexHome, 'hooks/pre-tool-use-edit-recall.sh'),
    path.join(codexHome, 'hooks/pre-tool-use-git-commit-capture.sh'),
    path.join(codexHome, 'hooks/kb-stop-digest.sh'),
    path.join(codexHome, 'skills/speckit-analyze/SKILL.md'),
    path.join(codexHome, 'skills/speckit-taskstoissues/SKILL.md'),
    path.join(codexHome, 'skills/kb-first/SKILL.md'),
    path.join(codexHome, '.knowledge-system-allowlist'),
    path.join(codexHome, '.knowledge-system-version'),
    path.join(codexHome, 'hooks.json'),
  ]
  const specifySeedFiles = manifest.specify.project_seed.targets.map((target) => path.join(projectRoot, target.path))
  for (const file of [...managedFiles, ...specifySeedFiles])
    assert.ok(existsSync(file), `project-scope installer wrote ${file}`)
  assert.ok(
    (await import('node:fs')).statSync(path.join(projectRoot, '.specify/scripts/bash/check-prerequisites.sh')).mode &
      0o111,
  )
  assert.equal(
    (await readFile(path.join(projectRoot, '.specify/memory/constitution.md'), 'utf8')).trimEnd(),
    (await readRepoText('.specify/memory/constitution.md')).trimEnd(),
  )
  assertContains(await readFile(path.join(claudeHome, '.knowledge-system-version'), 'utf8'), 'scope=project')
  assertContains(await readFile(path.join(codexHome, '.knowledge-system-version'), 'utf8'), 'scope=project')
  assert.equal(existsSync(ignoredClaudeHome), false)
  assert.equal(existsSync(ignoredCodexHome), false)
  const uninstallResult = await runProcess('bash', [installer, '--agent', 'all', '--scope', 'project', '--uninstall'], {
    env,
  })
  assert.equal(uninstallResult.exitCode, 0, uninstallResult.stderr)
  for (const file of managedFiles) assert.equal(existsSync(file), false, `project-scope uninstall removed ${file}`)
  for (const file of specifySeedFiles)
    assert.ok(existsSync(file), `project-scope uninstall should preserve project-owned Spec Kit seed ${file}`)
})

test('installed recall hooks parse payloads dedupe and honor allowlist', async () => {
  const dir = await tempDir()
  const { claudeHome, codexHome, installEnvironment } = await installAgentKit(dir, 'hook')
  const fakeBin = path.join(dir, 'bin')
  await mkdir(fakeBin, { recursive: true })
  const curlLog = path.join(dir, 'curl-payloads.jsonl')
  await writeExecutable(
    path.join(fakeBin, 'curl'),
    [
      '#!/usr/bin/env bash',
      'while [ "$#" -gt 0 ]; do',
      '  case "$1" in',
      '    -d) shift; printf \'%s\\n\' "$1" >> "${CURL_CAPTURE_FILE}" ;;',
      '  esac',
      '  shift || true',
      'done',
      "cat <<'JSON'",
      '{"jsonrpc":"2.0","result":{"structuredContent":{"hits":[{"title":"Prior capture","scope":"project:personal-stack","score":0.92,"id":"01H","snippet":"Remember this module"}]}}}',
      'JSON',
    ].join('\n'),
  )
  const hookEnvironment = {
    ...installEnvironment,
    KB_BEARER_TOKEN: 'test-token',
    KB_URL: 'http://knowledge.local',
    CURL_CAPTURE_FILE: curlLog,
    PATH: `${fakeBin}:${process.env.PATH}`,
  }
  const promptCurlLog = path.join(dir, 'prompt-curl-payloads.jsonl')
  const promptResult = await runProcess(path.join(claudeHome, 'hooks/user-prompt-submit-recall.sh'), [], {
    env: { ...hookEnvironment, CURL_CAPTURE_FILE: promptCurlLog },
    input: `{"user_prompt":"Please improve the personal-stack agent kit memory hooks and installer validation coverage."}`,
  })
  assert.equal(promptResult.exitCode, 0, promptResult.stderr)
  assertContains(promptResult.stdout, 'Knowledge base', 'Prior capture', 'score 0.92')
  const promptArgs = toolArguments((await readCurlPayloads(promptCurlLog))[0])
  assertContains(promptArgs.query, 'personal-stack agent kit memory hooks')
  assert.equal(promptArgs.mode, 'hybrid')
  assert.equal(promptArgs.limit, 3)

  const claudeResult = await runProcess(path.join(claudeHome, 'hooks/pre-tool-use-edit-recall.sh'), [], {
    env: { ...hookEnvironment, CLAUDE_SESSION_ID: 'claude-edit-session' },
    input: `{"tool_input":{"file_path":"platform/agents/kit/manifest.yaml"}}`,
  })
  assert.equal(claudeResult.exitCode, 0, claudeResult.stderr)
  const claudeArgs = toolArguments((await readCurlPayloads(curlLog))[0])
  assertContains(claudeArgs.query, 'manifest.yaml', 'platform/agents/kit/manifest.yaml')
  assert.equal(claudeArgs.scope, 'project:personal-stack')
  assert.equal(claudeArgs.mode, 'hybrid')
  assert.equal(claudeArgs.limit, 2)
  assertContains(claudeResult.stdout, 'Related captures for this file', 'Prior capture')
  await runProcess(path.join(claudeHome, 'hooks/pre-tool-use-edit-recall.sh'), [], {
    env: { ...hookEnvironment, CLAUDE_SESSION_ID: 'claude-edit-session' },
    input: `{"tool_input":{"file_path":"platform/agents/kit/manifest.yaml"}}`,
  })
  assert.equal((await readCurlPayloads(curlLog)).length, 1)

  const codexResult = await runProcess(path.join(codexHome, 'hooks/pre-tool-use-edit-recall.sh'), [], {
    env: { ...hookEnvironment, KB_AUTO_MCP_HOME: codexHome, CODEX_THREAD_ID: 'codex-edit-session' },
    input: `{"tool_input":{"file_path":"platform/agents/kit/render-agent-kit.py"}}`,
  })
  assert.equal(codexResult.exitCode, 0, codexResult.stderr)
  assertContains(
    await readFile(path.join(codexHome, 'hooks/pre-tool-use-edit-recall.sh'), 'utf8'),
    'Related captures for this file',
  )
  const codexArgs = toolArguments((await readCurlPayloads(curlLog)).at(-1))
  assertContains(codexArgs.query, 'render-agent-kit.py', 'platform/agents/kit/render-agent-kit.py')
  assert.equal(codexArgs.scope, 'project:personal-stack')
  await runProcess(path.join(codexHome, 'hooks/pre-tool-use-edit-recall.sh'), [], {
    env: { ...hookEnvironment, KB_AUTO_MCP_HOME: codexHome, CODEX_THREAD_ID: 'codex-secret-session' },
    input: `{"tool_input":{"file_path":".env"}}`,
  })
  assert.equal((await readCurlPayloads(curlLog)).length, 2)
})

test('installed capture hooks parse commits and stop digest sessions', async () => {
  const dir = await tempDir()
  const { claudeHome, codexHome, installEnvironment } = await installAgentKit(dir, 'capture')
  const fakeBin = path.join(dir, 'capture-bin')
  await mkdir(fakeBin, { recursive: true })
  await writeExecutable(
    path.join(fakeBin, 'curl'),
    [
      '#!/usr/bin/env bash',
      'payload=""',
      'while [ "$#" -gt 0 ]; do',
      '  case "$1" in',
      '    -d) shift; payload="$1"; printf \'%s\\n\' "${payload}" >> "${CURL_CAPTURE_FILE}" ;;',
      '  esac',
      '  shift || true',
      'done',
      'python3 - "$payload" <<\'PY\'',
      'import json, sys',
      'payload = json.loads(sys.argv[1])',
      'name = payload["params"]["name"]',
      'args = payload["params"].get("arguments", {})',
      'if name == "knowledge.digest_transcript":',
      '    transcript = args.get("transcript", "")',
      '    is_codex = "Codex stop transcript" in transcript',
      '    print(json.dumps({"jsonrpc":"2.0","result":{"structuredContent":{"candidates":[{"title":"Codex digest lesson" if is_codex else "Claude digest lesson","body":"Capture Codex stop lessons without duplicates." if is_codex else "Capture Claude stop lessons without duplicates.","suggested_topic":"" if is_codex else "agent-tools","suggested_tags":["agent-kit","hooks"]}]}}}))',
      'elif name == "knowledge.recall":',
      '    print(json.dumps({"jsonrpc":"2.0","result":{"structuredContent":{"hits":[]}}}))',
      'else:',
      '    print(json.dumps({"jsonrpc":"2.0","result":{"structuredContent":{"id":"01HOOK"}}}))',
      'PY',
    ].join('\n'),
  )
  const hookEnvironment = {
    ...installEnvironment,
    KB_BEARER_TOKEN: 'test-token',
    KB_URL: 'http://knowledge.local',
    PATH: `${fakeBin}:${process.env.PATH}`,
  }
  const commitCurlLog = path.join(dir, 'commit-curl-payloads.jsonl')
  const commitEnvironment = { ...hookEnvironment, CURL_CAPTURE_FILE: commitCurlLog }
  const claudeCommit = await runProcess(path.join(claudeHome, 'hooks/pre-tool-use-git-commit-capture.sh'), [], {
    env: commitEnvironment,
    input: `{"tool_name":"Bash","tool_input":{"command":"git commit -m \\"Add hook capture smoke\\""}}`,
  })
  assert.equal(claudeCommit.exitCode, 0, claudeCommit.stderr)
  const claudeCommitArgs = toolArguments((await readCurlPayloads(commitCurlLog))[0])
  assert.equal(claudeCommitArgs.title, 'Add hook capture smoke')
  assert.equal(claudeCommitArgs.scope, 'project:personal-stack')
  assert.equal(claudeCommitArgs.source, 'claude-code:auto-capture:git-commit')
  assertContains(claudeCommitArgs.body, 'Claude PreToolUse', '`git commit` hook')
  assert.deepEqual(claudeCommitArgs.tags, ['auto-capture', 'git-commit'])

  const codexCommit = await runProcess(path.join(codexHome, 'hooks/pre-tool-use-git-commit-capture.sh'), [], {
    env: {
      ...commitEnvironment,
      KB_AUTO_MCP_CLIENT_NAME: 'Codex',
      KB_AUTO_MCP_SOURCE: 'codex:auto-capture:git-commit',
    },
    input: `{"tool":{"name":"Bash","input":{"cmd":"git commit -m 'Add Codex commit capture'"}}}`,
  })
  assert.equal(codexCommit.exitCode, 0, codexCommit.stderr)
  const codexCommitArgs = toolArguments((await readCurlPayloads(commitCurlLog)).at(-1))
  assert.equal(codexCommitArgs.title, 'Add Codex commit capture')
  assert.equal(codexCommitArgs.scope, 'project:personal-stack')
  assert.equal(codexCommitArgs.source, 'codex:auto-capture:git-commit')
  assertContains(codexCommitArgs.body, 'Codex', '`git commit` hook')
  await runProcess(path.join(claudeHome, 'hooks/pre-tool-use-git-commit-capture.sh'), [], {
    env: commitEnvironment,
    input: `{"tool_name":"Bash","tool_input":{"command":"git commit -m \\"WIP scratch\\""}}`,
  })
  assert.equal((await readCurlPayloads(commitCurlLog)).length, 2)

  const claudeTranscript = path.join(dir, 'claude-transcript.jsonl')
  await writeFile(
    claudeTranscript,
    `{"role":"user","content":"Claude stop transcript should create a durable lesson."}\n`,
  )
  const claudeStopCurlLog = path.join(dir, 'claude-stop-curl-payloads.jsonl')
  const claudeStopInput = JSON.stringify({ session_id: 'claude-stop-session', transcript_path: claudeTranscript })
  const claudeStop = await runProcess(path.join(claudeHome, 'hooks/stop-session-digest.sh'), [], {
    env: { ...hookEnvironment, CURL_CAPTURE_FILE: claudeStopCurlLog, KB_DIGEST_MAX_CAPTURES: '1' },
    input: claudeStopInput,
  })
  assert.equal(claudeStop.exitCode, 0, claudeStop.stderr)
  const claudeStopPayloads = await readCurlPayloads(claudeStopCurlLog)
  assert.deepEqual(claudeStopPayloads.map(toolName), [
    'knowledge.digest_transcript',
    'knowledge.recall',
    'knowledge.capture_lesson',
  ])
  assertContains(toolArguments(claudeStopPayloads[0]).transcript, 'Claude stop transcript')
  assert.equal(toolArguments(claudeStopPayloads[0]).max_candidates, 1)
  assert.equal(toolArguments(claudeStopPayloads[1]).mode, 'hybrid')
  const claudeCaptureArgs = toolArguments(claudeStopPayloads[2])
  assert.equal(claudeCaptureArgs.title, 'Claude digest lesson')
  assert.equal(claudeCaptureArgs.scope, 'topic:agent-tools')
  assert.equal(claudeCaptureArgs.source, 'claude-code:auto-digest:claude-stop-session')
  assert.equal(claudeCaptureArgs.session_id, 'claude-stop-session')
  assert.deepEqual(claudeCaptureArgs.tags, ['agent-kit', 'hooks'])
  assert.equal(
    (await readFile(path.join(claudeHome, 'state/sessions/claude-stop-session/digest-budget'), 'utf8')).trim(),
    '0',
  )
  await runProcess(path.join(claudeHome, 'hooks/stop-session-digest.sh'), [], {
    env: { ...hookEnvironment, CURL_CAPTURE_FILE: claudeStopCurlLog, KB_DIGEST_MAX_CAPTURES: '1' },
    input: claudeStopInput,
  })
  assert.equal((await readCurlPayloads(claudeStopCurlLog)).length, 3)

  const codexTranscript = path.join(dir, 'codex-transcript.jsonl')
  await writeFile(
    codexTranscript,
    `{"source":"user","message":"Codex stop transcript should fall back to project scope."}\n`,
  )
  const codexStopCurlLog = path.join(dir, 'codex-stop-curl-payloads.jsonl')
  const codexStop = await runProcess(path.join(codexHome, 'hooks/kb-stop-digest.sh'), [], {
    env: {
      ...hookEnvironment,
      CODEX_HOME: codexHome,
      CURL_CAPTURE_FILE: codexStopCurlLog,
      KB_DIGEST_MAX_CAPTURES: '1',
    },
    input: JSON.stringify({ thread_id: 'codex-stop-session', transcriptPath: codexTranscript }),
  })
  assert.equal(codexStop.exitCode, 0, codexStop.stderr)
  const codexStopPayloads = await readCurlPayloads(codexStopCurlLog)
  assert.deepEqual(codexStopPayloads.map(toolName), [
    'knowledge.digest_transcript',
    'knowledge.recall',
    'knowledge.capture_lesson',
  ])
  const codexCaptureArgs = toolArguments(codexStopPayloads[2])
  assert.equal(codexCaptureArgs.title, 'Codex digest lesson')
  assert.equal(codexCaptureArgs.scope, 'project:personal-stack')
  assert.equal(codexCaptureArgs.source, 'codex:auto-digest:codex-stop-session')
  assert.equal(codexCaptureArgs.session_id, 'codex-stop-session')
  assert.equal(
    (await readFile(path.join(codexHome, 'state/sessions/codex-stop-session/digest-budget'), 'utf8')).trim(),
    '0',
  )
})

test('installed hooks stay silent when disabled or unauthenticated', async () => {
  const dir = await tempDir()
  const { claudeHome, codexHome, installEnvironment } = await installAgentKit(dir, 'silent')
  const curlLog = path.join(dir, 'silent-curl-calls.log')
  const fakeBin = path.join(dir, 'silent-bin')
  await mkdir(fakeBin, { recursive: true })
  await writeExecutable(
    path.join(fakeBin, 'curl'),
    [
      '#!/usr/bin/env bash',
      'printf \'called\\n\' >> "${CURL_CAPTURE_FILE}"',
      "cat <<'JSON'",
      '{"jsonrpc":"2.0","result":{"structuredContent":{"hits":[],"candidates":[]}}}',
      'JSON',
    ].join('\n'),
  )
  const claudeTranscript = path.join(dir, 'silent-claude-transcript.jsonl')
  const codexTranscript = path.join(dir, 'silent-codex-transcript.jsonl')
  await writeFile(
    claudeTranscript,
    `{"role":"user","content":"Claude stop transcript should not call MCP when disabled."}\n`,
  )
  await writeFile(
    codexTranscript,
    `{"source":"user","message":"Codex stop transcript should not call MCP when disabled."}\n`,
  )
  const invocations = [
    [
      `{"user_prompt":"Recall should stay silent when disabled."}`,
      path.join(claudeHome, 'hooks/user-prompt-submit-recall.sh'),
      {},
    ],
    [
      `{"user_prompt":"Recall should stay silent when disabled."}`,
      path.join(codexHome, 'hooks/kb-user-prompt-recall.sh'),
      {},
    ],
    [
      `{"tool_input":{"file_path":"platform/agents/kit/manifest.yaml"}}`,
      path.join(claudeHome, 'hooks/pre-tool-use-edit-recall.sh'),
      {},
    ],
    [
      `{"tool_input":{"file_path":"platform/agents/kit/manifest.yaml"}}`,
      path.join(codexHome, 'hooks/pre-tool-use-edit-recall.sh'),
      { KB_AUTO_MCP_HOME: codexHome },
    ],
    [
      `{"tool_name":"Bash","tool_input":{"command":"git commit -m \\"Capture hook silence\\""}}`,
      path.join(claudeHome, 'hooks/pre-tool-use-git-commit-capture.sh'),
      {},
    ],
    [
      `{"tool":{"name":"Bash","input":{"cmd":"git commit -m 'Capture Codex silence'"}}}`,
      path.join(codexHome, 'hooks/pre-tool-use-git-commit-capture.sh'),
      { KB_AUTO_MCP_CLIENT_NAME: 'Codex', KB_AUTO_MCP_SOURCE: 'codex:auto-capture:git-commit' },
    ],
    [
      JSON.stringify({ session_id: 'silent-claude', transcript_path: claudeTranscript }),
      path.join(claudeHome, 'hooks/stop-session-digest.sh'),
      {},
    ],
    [
      JSON.stringify({ thread_id: 'silent-codex', transcriptPath: codexTranscript }),
      path.join(codexHome, 'hooks/kb-stop-digest.sh'),
      { CODEX_HOME: codexHome },
    ],
  ]
  const hookEnvironment = {
    ...installEnvironment,
    KB_URL: 'http://knowledge.local',
    CURL_CAPTURE_FILE: curlLog,
    PATH: `${fakeBin}:${process.env.PATH}`,
  }
  for (const [input, command, env] of invocations) {
    const result = await runProcess(command, [], {
      env: { ...hookEnvironment, ...env, KB_BEARER_TOKEN: 'test-token', KB_AUTO_MCP_DISABLED: '1' },
      input,
    })
    assert.equal(result.exitCode, 0, result.stderr)
  }
  assert.equal(existsSync(curlLog), false)
  for (const [input, command, env] of invocations) {
    const result = await runProcess(command, [], { env: { ...hookEnvironment, ...env, KB_BEARER_TOKEN: '' }, input })
    assert.equal(result.exitCode, 0, result.stderr)
  }
  assert.equal(existsSync(curlLog), false)
})

test('hook settings reference only manifest hooks', async () => {
  const manifestHookPaths = new Set(manifestTargetPaths('hooks').filter((item) => item.includes('/hooks/')))
  const settingsHookPaths = new Set()
  for (const file of ['.claude/settings.json', '.codex/hooks.json']) {
    const text = await readRepoText(file)
    for (const match of text.matchAll(/\.(?:claude|codex)\/hooks\/[A-Za-z0-9._-]+\.sh/g))
      settingsHookPaths.add(match[0])
  }
  assertSameMembers(
    settingsHookPaths,
    manifestHookPaths,
    'checked-in hook settings must not reference scripts outside the agent-kit manifest',
  )
})

test('hook tool calls use canonical knowledge mcp names', async () => {
  const knownKnowledgeTools = await canonicalKnowledgeToolNames()
  const hookFiles = [...new Set([...manifestTargetPaths('hooks'), manifest.installer.path])]
  const referencedTools = new Set()
  const legacyNames = []
  for (const file of hookFiles) {
    const text = await readRepoText(file)
    for (const match of text.matchAll(/["']name["']\s*:\s*["'](knowledge\.[A-Za-z_]+)["']/g))
      referencedTools.add(match[1])
    for (const match of text.matchAll(/knowledge_(?:recall|capture_lesson|capture_decision|digest_transcript)/g))
      legacyNames.push(`${file}:${match[0]}`)
  }
  for (const tool of referencedTools)
    assert.ok(knownKnowledgeTools.has(tool), `${tool} should be a canonical knowledge tool`)
  assert.deepEqual(legacyNames, [])
  for (const hook of manifestItems('hooks')) {
    for (const tool of hook.mcp_tools ?? [])
      assert.ok(knownKnowledgeTools.has(tool), `${hook.name} declares canonical tool ${tool}`)
  }
})

test('recall injection hooks fall back to fast mode', async () => {
  for (const file of ['.claude/hooks/kb-user-prompt-recall.sh', '.codex/hooks/kb-user-prompt-recall.sh']) {
    const script = await readRepoText(file)
    assertContains(script, `[ "\${mode}" != "fast" ]`, `call_recall "\${prompt}" "\${limit}" fast`)
  }
  for (const file of ['.claude/hooks/pre-tool-use-edit-recall.sh', '.codex/hooks/pre-tool-use-edit-recall.sh']) {
    const script = await readRepoText(file)
    assertContains(
      script,
      `[ "\${mode}" != "fast" ]`,
      `call_recall "\${query}" "\${limit}" fast "\${scope}"`,
      `args["scope"] = sys.argv[4]`,
    )
  }
  const installer = await readRepoText(manifest.installer.path)
  assert.ok([...installer.matchAll(/call_recall "\$\{(?:prompt|query)}" "\$\{limit}" fast/g)].length >= 2)
})

test('renderer templates are declared in the manifest', async () => {
  const templatePaths = await rendererTemplatePaths()
  const managedPathList = rendererManagedPathList()
  const managedPaths = new Set(managedPathList)
  const pinnedPaths = new Set(collectPinnedPaths(manifest).map((item) => item.path))
  assert.equal(
    new Set(managedPathList).size,
    managedPathList.length,
    'renderer managed paths must not contain duplicates',
  )
  assertSameMembers(templatePaths, managedPaths, 'renderer templates must match manifest managed paths exactly')
  for (const managedPath of managedPaths)
    assert.ok(pinnedPaths.has(managedPath), `${managedPath} is pinned by manifest sha256`)
})

test('renderer include partials are declared and resolvable', async () => {
  const declaredPartials = new Set(manifest.renderer.include_templates ?? [])
  const referencedPartials = await rendererIncludeTemplatePaths()
  assertSameMembers(
    declaredPartials,
    referencedPartials,
    'renderer include partials must be explicit manifest inventory',
  )
  for (const file of declaredPartials) assert.ok(existsSync(repoPath(file)), `renderer include partial exists: ${file}`)
})

test('renderer check passes and can render templates to a temp directory', async () => {
  const dir = await tempDir()
  const renderer = repoPath(manifest.renderer.script_path)
  assert.ok(
    (await import('node:fs')).statSync(renderer).mode & 0o111,
    'agent-kit renderer should be directly executable',
  )
  const checkResult = await runProcess(renderer, ['--check'])
  if (checkResult.exitCode === 0) {
    assertContains(checkResult.stdout, 'agent kit render check passed')
  } else {
    const missingGeneratedSkillLines = `${checkResult.stdout}${checkResult.stderr}`.split('\n').filter(Boolean)
    for (const line of missingGeneratedSkillLines)
      assert.match(line, /^missing: \.agents\/skills\/speckit-[^/]+\/SKILL\.md$/)
    assert.equal(
      missingGeneratedSkillLines.length,
      [...(await repoSkillPaths())].filter((item) => item.startsWith('.agents/skills/speckit-')).length,
    )
  }
  const outputDir = path.join(dir, 'agent-kit-render')
  const renderResult = await runProcess(renderer, ['--output', outputDir])
  assert.equal(renderResult.exitCode, 0, renderResult.stderr)
  for (const managedPath of rendererManagedPaths()) {
    assert.deepEqual(
      await readFile(path.join(outputDir, managedPath)),
      await readFile(rendererSourcePath(managedPath)),
      `rendered output for ${managedPath}`,
    )
  }
})

test('portability runbook documents install scope export restore and compatibility matrix', async () => {
  const runbook = await readRepoText('platform/agents/kit/PORTABILITY.md')
  assertContains(
    runbook,
    '--scope user',
    '--scope project',
    'knowledge-vault',
    'Postgres logical backup',
    'Restore Order',
    'Compatibility Matrix',
    'Claude Code',
    'Codex',
    'knowledge.recall',
  )
})

test('agent kit doctor reports static health and skipped live checks', async () => {
  const dir = await tempDir()
  const renderer = repoPath(manifest.renderer.script_path)
  const home = path.join(dir, 'doctor-home')
  const result = await runProcess(renderer, ['--doctor'], {
    env: {
      HOME: home,
      CLAUDE_CONFIG_DIR: path.join(home, '.claude'),
      CODEX_HOME: path.join(home, '.codex'),
      KB_URL: '',
      KB_BEARER_TOKEN: '',
    },
  })
  assert.equal(result.exitCode, 0, result.stderr)
  assertContains(
    result.stdout,
    'agent kit doctor',
    'ok   render: generated files match templates',
    'ok   manifest: kit manifest version 2',
    'ok   mcp-profiles: 5 synchronized profiles; minimal 2 Claude/2 Codex servers; full-diagnostic 7 Claude/7 Codex servers',
    'warn claude-install: manifest missing',
    'warn codex-install: manifest missing',
    'warn kb-live: KB_URL is not set; live MCP probe skipped',
    'summary: 3 ok, 3 warn, 0 fail',
  )
})

test('agent kit doctor validates installed manifest versions when expected version is set', async () => {
  const dir = await tempDir()
  const renderer = repoPath(manifest.renderer.script_path)
  const home = path.join(dir, 'doctor-installed')
  const claudeHome = path.join(home, '.claude')
  const codexHome = path.join(home, '.codex')
  await mkdir(claudeHome, { recursive: true })
  await mkdir(codexHome, { recursive: true })
  await writeFile(path.join(claudeHome, '.knowledge-system-version'), 'version=current\n')
  await writeFile(path.join(codexHome, '.knowledge-system-version'), 'version=current\nagent=codex\n')
  const result = await runProcess(renderer, ['--doctor'], {
    env: {
      HOME: home,
      CLAUDE_CONFIG_DIR: claudeHome,
      CODEX_HOME: codexHome,
      AGENT_KIT_EXPECTED_VERSION: 'current',
      KB_URL: '',
      KB_BEARER_TOKEN: '',
    },
  })
  assert.equal(result.exitCode, 0, result.stderr)
  assertContains(
    result.stdout,
    'ok   manifest: kit manifest version 2',
    'ok   mcp-profiles: 5 synchronized profiles; minimal 2 Claude/2 Codex servers; full-diagnostic 7 Claude/7 Codex servers',
    'ok   claude-install: manifest version current, expected current',
    'ok   codex-install: manifest version current, expected current',
    'summary: 5 ok, 1 warn, 0 fail',
  )
})

test('agent kit doctor can require live kb credentials', async () => {
  const dir = await tempDir()
  const renderer = repoPath(manifest.renderer.script_path)
  const home = path.join(dir, 'doctor-live')
  const result = await runProcess(renderer, ['--doctor', '--require-live-kb'], {
    env: {
      HOME: home,
      CLAUDE_CONFIG_DIR: path.join(home, '.claude'),
      CODEX_HOME: path.join(home, '.codex'),
      KB_URL: 'http://knowledge.local',
      KB_BEARER_TOKEN: '',
    },
  })
  assert.equal(result.exitCode, 1, result.stdout)
  assertContains(
    result.stdout,
    'fail kb-live: KB_BEARER_TOKEN is not set; live MCP probe skipped',
    'summary: 3 ok, 2 warn, 1 fail',
  )
})

test('agent kit doctor probes tools list and fast recall when live kb credentials exist', async (t) => {
  const dir = await tempDir()
  const renderer = repoPath(manifest.renderer.script_path)
  const home = path.join(dir, 'doctor-live-ok')
  const payloads = []
  const server = http.createServer((request, response) => {
    let body = ''
    request.setEncoding('utf8')
    request.on('data', (chunk) => {
      body += chunk
    })
    request.on('end', () => {
      const payload = JSON.parse(body)
      payloads.push(payload)
      const out =
        payload.method === 'tools/list'
          ? {
              jsonrpc: '2.0',
              id: 'agent-kit-doctor-tools',
              result: { tools: [{ name: 'knowledge.recall' }, { name: 'knowledge.capture_lesson' }] },
            }
          : payload.method === 'tools/call'
            ? {
                jsonrpc: '2.0',
                id: 'agent-kit-doctor-recall',
                result: { structuredContent: { hits: [{ id: '01H', title: 'Prior note' }] } },
              }
            : { jsonrpc: '2.0', error: { code: -32601, message: 'method not found' } }
      response.writeHead(200, { 'Content-Type': 'application/json' })
      response.end(JSON.stringify(out))
    })
  })
  const listenResult = await new Promise((resolve) => {
    server.once('error', (error) => resolve(error))
    server.listen(0, '127.0.0.1', () => resolve(null))
  })
  if (listenResult?.code === 'EPERM') {
    server.close()
    t.skip('sandbox does not permit binding a local HTTP server for the live KB doctor probe')
    return
  }
  assert.equal(listenResult, null)
  try {
    const port = server.address().port
    const result = await runProcess(renderer, ['--doctor'], {
      env: {
        HOME: home,
        CLAUDE_CONFIG_DIR: path.join(home, '.claude'),
        CODEX_HOME: path.join(home, '.codex'),
        KB_URL: `http://127.0.0.1:${port}`,
        KB_BEARER_TOKEN: 'test-token',
      },
    })
    assert.equal(result.exitCode, 0, result.stderr)
    assertContains(
      result.stdout,
      `ok   kb-live: reachable at http://127.0.0.1:${port}/mcp with 2 tools; fast recall returned 1 hits`,
      'summary: 4 ok, 2 warn, 0 fail',
    )
    assert.deepEqual(
      payloads.map((payload) => payload.method),
      ['tools/list', 'tools/call'],
    )
    const recallPayload = payloads[1]
    assert.equal(recallPayload.params.name, 'knowledge.recall')
    assertContains(recallPayload.params.arguments.query, 'agent kit doctor')
    assert.equal(recallPayload.params.arguments.scope, 'project:personal-stack')
    assert.equal(recallPayload.params.arguments.mode, 'fast')
    assert.equal(recallPayload.params.arguments.limit, 1)
  } finally {
    await new Promise((resolve) => server.close(resolve))
  }
})

test('knowledge-api installer placeholders and public routes stay exposed', async () => {
  const installer = await readRepoText('services/knowledge-api/src/main/resources/installer/install.sh')
  assertContains(
    installer,
    "readonly INSTALLER_VERSION='@VERSION@'",
    "readonly KB_URL='@KB_URL@'",
    'KB_URL="${KB_URL:-@KB_URL@}"',
    '${KB_URL}/install.sh',
  )
  const installAgents = await readRepoText('services/knowledge-api/src/main/resources/installer/install-agents.sh')
  assertContains(installAgents, "readonly INSTALLER_VERSION='@VERSION@'", "readonly KB_URL='@KB_URL@'", '${KB_URL%/}/install.sh')
  const fleet = await readYamlRepo('platform/inventory/fleet.yaml')
  const route = fleet.ingress_intent.route_rules.find((item) => item.name === 'knowledge-api-mcp')
  assert.equal(route.service, 'knowledge-api')
  assert.equal(route.access, 'direct')
  assertSameMembers(route.exact_paths, ['/mcp', '/install.sh', '/install-agents.sh'])
  assert.deepEqual(route.path_prefixes, ['/mcp/'])
  const appRoute = fleet.ingress_intent.route_rules.find(
    (item) => item.service === 'knowledge-api' && item.name !== 'knowledge-api-mcp',
  )
  assertSameMembers(appRoute.excluded_exact_paths, ['/mcp', '/install.sh', '/install-agents.sh'])
  assert.deepEqual(appRoute.excluded_path_prefixes, ['/mcp/'])
  const catalog = await readRepoText('platform/cluster/flux/apps/edge/edge-route-catalog-configmap.yaml')
  const ingressRoutes = await readRepoText('platform/cluster/flux/apps/edge/traefik-ingressroutes.yaml')
  assertContains(catalog, 'name: "knowledge-api-mcp"', '- "/mcp"', '- "/install.sh"', '- "/install-agents.sh"', '- "/mcp/"')
  assertContains(
    ingressRoutes,
    'name: knowledge-api-mcp',
    'Path(`/install.sh`)',
    'Path(`/install-agents.sh`)',
    'Path(`/mcp`)',
    'PathPrefix(`/mcp/`)',
  )
})

async function installAgentKit(dir, prefix) {
  const claudeHome = path.join(dir, `${prefix}-claude`)
  const codexHome = path.join(dir, `${prefix}-codex`)
  const installEnvironment = { CLAUDE_CONFIG_DIR: claudeHome, CODEX_HOME: codexHome }
  const installResult = await runProcess('bash', [repoPath(manifest.installer.path), '--agent', 'all'], {
    env: installEnvironment,
  })
  assert.equal(installResult.exitCode, 0, installResult.stderr)
  return { claudeHome, codexHome, installEnvironment }
}

async function repoSkillPaths() {
  const paths = []
  for (const base of ['.agents/skills', '.claude/skills']) {
    const root = rendererSourceRoot(base)
    for (const file of await filesUnder(root))
      if (path.basename(file) === 'SKILL.md') paths.push(`${base}/${relativePath(root, file)}`)
  }
  return new Set(paths)
}

async function repoCommandPaths() {
  const root = rendererSourceRoot('.claude/commands')
  return new Set(
    (await filesUnder(root))
      .filter((file) => path.basename(file).startsWith('speckit.') && file.endsWith('.md'))
      .map((file) => `.claude/commands/${relativePath(root, file)}`),
  )
}

async function repoHookPaths() {
  const paths = []
  for (const base of ['.claude/hooks', '.codex/hooks']) {
    const root = rendererSourceRoot(base)
    for (const file of await filesUnder(root)) paths.push(`${base}/${relativePath(root, file)}`)
  }
  return new Set(paths)
}

async function skillNamesUnder(base) {
  const root = rendererSourceRoot(base)
  return new Set((await filesUnder(root)).map((file) => path.relative(root, file).split(path.sep)[0]).filter(Boolean))
}

function manifestItems(section) {
  return manifest[section] ?? []
}

function manifestTargetPaths(section) {
  return manifestItems(section).flatMap(itemTargetPaths)
}

function itemTargetPaths(item) {
  return (item.targets ?? []).map((target) => target.path).filter(Boolean)
}

function supportedAgents(item) {
  return item.supported_agents ?? []
}

function collectPinnedPaths(node) {
  const out = []
  function walk(current) {
    if (Array.isArray(current)) {
      for (const item of current) walk(item)
      return
    }
    if (current && typeof current === 'object') {
      if (current.path && current.sha256) out.push({ path: current.path, sha256: current.sha256 })
      for (const value of Object.values(current)) walk(value)
    }
  }
  walk(node)
  return out
}

function assertAgentGapIsExplicit(label, node) {
  const missingAgents = ['claude', 'codex'].filter((agent) => !supportedAgents(node).includes(agent))
  for (const agent of missingAgents)
    assert.ok(node.unsupported?.[agent]?.trim(), `${label} unsupported reason for ${agent}`)
}

async function canonicalKnowledgeToolNames() {
  const text = await readRepoText(
    'services/knowledge-api/src/test/kotlin/com/jorisjonkers/personalstack/knowledge/mcp/McpToolsTest.kt',
  )
  return new Set([...text.matchAll(/"(knowledge\.[a-z_]+)"/g)].map((match) => match[1]))
}

function hookMatchers(settings, event) {
  return settings.hooks[event].map((group) => group.matcher).filter(Boolean)
}

function hookCommands(settings, event) {
  return settings.hooks[event].flatMap((group) => group.hooks.map((hook) => hook.command))
}

async function readCurlPayloads(file) {
  if (!existsSync(file)) return []
  return (await readFile(file, 'utf8'))
    .split('\n')
    .filter(Boolean)
    .map((line) => JSON.parse(line))
}

function toolArguments(payload) {
  return payload.params.arguments
}

function toolName(payload) {
  return payload.params.name
}

function rendererManagedPathList() {
  return manifest.renderer.managed_paths
}

function rendererManagedPaths() {
  return new Set(rendererManagedPathList())
}

async function rendererTemplatePaths() {
  const templateRoot = repoPath(manifest.renderer.template_root)
  const repoTemplatePaths = (await filesUnder(templateRoot)).map((file) => relativePath(templateRoot, file))
  const extraTemplatePaths = []
  for (const mapping of manifest.renderer.extra_templates ?? []) {
    assert.ok(existsSync(repoPath(mapping.source_path)), `extra renderer template exists: ${mapping.source_path}`)
    extraTemplatePaths.push(mapping.destination_path)
  }
  return new Set([...repoTemplatePaths, ...extraTemplatePaths])
}

async function rendererIncludeTemplatePaths() {
  const out = new Set()
  for (const mapping of manifest.renderer.extra_templates ?? []) {
    const source = repoPath(mapping.source_path)
    const templateRoot = path.dirname(source)
    const text = await readFile(source, 'utf8')
    for (const match of text.matchAll(/^# @agent-kit-include ([A-Za-z0-9_./-]+)$/gm)) {
      out.add(relativePath(repoRoot, path.join(templateRoot, match[1])))
    }
  }
  return out
}

function rendererSourceRoot(base) {
  const templateRoot = repoPath(manifest.renderer.template_root, base)
  return existsSync(templateRoot) ? templateRoot : repoPath(base)
}

function rendererSourcePath(itemPath) {
  const livePath = repoPath(itemPath)
  if (existsSync(livePath)) return livePath
  const templatePath = repoPath(manifest.renderer.template_root, itemPath)
  return existsSync(templatePath) ? templatePath : livePath
}
