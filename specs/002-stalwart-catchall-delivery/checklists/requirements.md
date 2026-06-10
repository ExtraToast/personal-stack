# Requirements Checklist: 002-stalwart-catchall-delivery

- [ ] CHK001 Root cause is correctly attributed to upstream M365 routing, not Stalwart
  catch-all config (evidence: `5.7.520`/`AS(4810)` from a Microsoft host). (Context, FR-001)
- [ ] CHK002 Runbook distinguishes blocker #1 (MX) from #2 (M365 accepted domain) with a
  decisive sender-based test. (FR-001)
- [ ] CHK003 Runbook lists every record (MX, SPF, DKIM, DMARC, MTA-STS, mail A) with values
  and the M365 accepted-domain removal + verification checklist. (US3)
- [ ] CHK004 Catch-all target mailbox `joris.jonkers` declared in `accounts.json`. (FR-002)
- [ ] CHK005 `extratoast` resolves to the target mailbox via alias. (FR-003)
- [ ] CHK006 New mailbox password is Vault-backed and **optional** (unset = safe no-op,
  no reset of existing mailbox). (FR-004)
- [ ] CHK007 Automated test fails if `STALWART_CATCHALL` points at an address with no
  backing account/alias. (FR-005, SC-004)
- [ ] CHK008 Test harness runs offline (sh + jq, no network, no live server). (FR-006)
- [ ] CHK009 CI stays green; test wired into the platform/infra path filter. (FR-007)
- [ ] CHK010 No secrets committed to git. (FR-004)
