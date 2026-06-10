# Runbook: Inbound mail for `jorisjonkers.dev` not delivered to Stalwart

**Related**: spec [`002-stalwart-catchall-delivery`](../../specs/002-stalwart-catchall-delivery/spec.md),
spec [`003`](../../specs/003-stalwart-mx-and-account-fix/spec.md).

Stalwart owns the `jorisjonkers.dev` DNS zone via the Cloudflare API token
(`secret/platform/edge` → `cloudflare.dns_api_token`) and `dnsManagement: Automatic` on
the domain. It publishes the zone's mail records (MX, SPF, DKIM, DMARC) itself; they are
not hand-maintained. So the failure is in what Stalwart publishes, not in a missing manual
record.

## Root cause: the published MX target was the pod name

Observed live state:

```
$ dig +short MX jorisjonkers.dev
10 stalwart-78587fc555-dfdr5.        # a Kubernetes pod name, not a real host
$ dig +short A mail.jorisjonkers.dev
167.86.79.203                        # correct
```

The MX records Stalwart publishes come from its `mailExchangers` setting — Stalwart's DNS
record builder (`crates/common/src/network/dns/records.rs`) iterates `system.mail_exchangers`
to emit the domain's MX. At first boot Stalwart seeds a single mail-exchanger entry whose
hostname is the container's OS hostname and persists it. In k8s the OS hostname is the pod
name (`stalwart-<replicaset>-<rand>`), so Stalwart published an MX pointing at a single-label,
non-resolvable pod name. Remote senders resolve the MX, fail to resolve its target, and never
reach Stalwart. Setting `defaultHostname` does **not** fix this — the MX host is taken from
the explicit `mailExchangers` entry, not the default hostname. (`server.hostname` itself,
exposed on the Bootstrap singleton, is locked after bootstrap — `update Bootstrap` returns
`forbidden: only allowed in bootstrap mode` — so it is not the lever here.)

This also explains "no bounce but not received": a Microsoft-origin sender's mail is held by
Microsoft (see the M365 note below), and a local/test send to an address with no real
mailbox is swallowed by the catch-all chain.

## Fix (automated, in-repo)

`infra/stalwart/apply.sh` now pins the published MX host on every reconcile, as part of the
mutable `SystemSettings` update:

```
{"@type":"update","object":"SystemSettings","value":{
   "defaultHostname":"mail.jorisjonkers.dev",
   "defaultDomainId":"<id>",
   "mailExchangers":{"0":{"hostname":"mail.jorisjonkers.dev","priority":10}}}}
```

Setting `mailExchangers` alone is not enough: Stalwart enqueues the DNS publish task only
when `dns_management` transitions into Automatic (`crates/jmap/src/registry/mapping/domain.rs`),
so re-asserting Automatic when it is already Automatic never pushes the corrected record — which
is why the bogus MX survived every roll. The reconcile therefore flips `dns_management` to
Manual and back to Automatic (Manual schedules no task and deletes nothing), forcing a full
re-publish of the record set — including the corrected MX — on every reconcile. On the next
`stalwart-tools:latest` roll, Stalwart republishes `jorisjonkers.dev. MX 10 mail.jorisjonkers.dev.`
to Cloudflare.

### Verify after the pod rolls

```sh
dig +short MX jorisjonkers.dev            # expect: 10 mail.jorisjonkers.dev
dig +short A  mail.jorisjonkers.dev       # expect: 167.86.79.203
kubectl -n mail-system logs deploy/stalwart -c stalwart-apply | grep -i SystemSettings
```

`server.hostname` (SMTP greeting / TLS cert hostname) stays whatever bootstrap set; it does
not affect the published MX and cannot be changed through the management object API. Correct
it from the webadmin (Settings → Server → Network → Hostname) only if the SMTP greeting
hostname matters for a given remote.

## Catch-all destination mailbox / account identity

`STALWART_CATCHALL=joris.jonkers@jorisjonkers.dev`, but the operator mailbox is currently a
manually-created account whose **primary** address is `extratoast@jorisjonkers.dev`, so
`joris.jonkers@` is not a deliverable address and the catch-all has nowhere to land. The
declarative target is `infra/stalwart/accounts.json`:

```json
{
  "localPart": "joris.jonkers",
  "displayName": "Joris Jonkers",
  "passwordEnv": "JORIS_MAIL_PASSWORD",
  "aliases": ["extratoast"]
}
```

i.e. primary `joris.jonkers@`, full name "Joris Jonkers", `extratoast@` as an alias. The
reconcile manages it only once its Vault password exists, and it locates accounts by their
**primary** address — it cannot rename the primary of the existing `extratoast@`-primary
mailbox. Pick one path:

- **Preserve the existing mailbox (recommended).** In the Stalwart webadmin, edit the
  current account: set the primary email to `joris.jonkers@jorisjonkers.dev`, set the full
  name to `Joris Jonkers`, and add `extratoast@jorisjonkers.dev` as an alias. Stored mail is
  kept. Then set Vault `secret/platform/mail` → `joris.password` so the reconcile keeps it
  converged.
- **Start clean.** Set Vault `joris.password`, delete the old `extratoast@`-primary account,
  and let the reconcile create `joris.jonkers` (primary + alias + name) from
  `accounts.json`. The old mailbox's stored mail is lost.

Until Vault `joris.password` is set, the reconcile skips the account (safe no-op).

## Microsoft 365 note (Microsoft-origin senders only)

An earlier bounce came from a Microsoft EOP host (`550 5.7.520 ... AS(4810)`), which means
`jorisjonkers.dev` is still a verified accepted domain in a Microsoft 365 tenant. Microsoft
routes mail from any Microsoft-hosted sender (outlook.com, live.nl, other M365 tenants)
**internally** and never queries the public MX — so those senders bypass Stalwart regardless
of the (now-fixed) MX. Non-Microsoft senders (Gmail, etc.) follow the public MX to Stalwart.

To make Stalwart authoritative for all senders, remove `jorisjonkers.dev` as an accepted
domain from the M365 tenant (Microsoft 365 admin → Settings → Domains, or Exchange admin →
Mail flow → Accepted domains) after migrating any wanted mail out of the M365 mailboxes.
This is an operator action in the Microsoft tenant; it cannot be done from this repo.

## Post-fix verification checklist

- [ ] `dig +short MX jorisjonkers.dev` → `10 mail.jorisjonkers.dev`.
- [ ] Test from **Gmail** to `joris.jonkers@jorisjonkers.dev` → delivered to the mailbox.
- [ ] Test from **Gmail** to `something@jorisjonkers.dev` (unknown) → delivered via catch-all
      to the `joris.jonkers` mailbox.
- [ ] Test to `extratoast@jorisjonkers.dev` → same mailbox.
- [ ] Microsoft-origin sender works **after** the M365 accepted-domain removal.
- [ ] Outbound from Stalwart passes SPF + DKIM + DMARC (e.g. mail-tester.com 9–10/10).
