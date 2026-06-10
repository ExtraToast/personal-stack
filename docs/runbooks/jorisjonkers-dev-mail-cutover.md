# Runbook: Cut `jorisjonkers.dev` mail over from Microsoft 365 to Stalwart

**Status**: Design-first. This documents an operator-executed cutover. Per
`dns-zone-policy.md`, do **not** copy the consumer Cloudflare zone file into this repo —
the values below are the records to set, applied by the operator in Cloudflare.

**Related**: spec [`002-stalwart-catchall-delivery`](../../specs/002-stalwart-catchall-delivery/spec.md).

## Why this is needed

Mail to `*@jorisjonkers.dev` is being delivered into Microsoft 365, so Stalwart (and its
catch-all) never see it. The proof is the bounce:

```
Generating server: ...PROD.OUTLOOK.COM
'554 5.7.0 < #5.7.520 smtp;550 5.7.520 Message blocked ... spam. AS(4810)>'
```

`5.7.520 / AS(4810)` is a Microsoft Exchange Online Protection verdict — a Microsoft host
rejected it, not `mail.jorisjonkers.dev`. The mail never reached Stalwart.

## Step 0 — Diagnose which blocker is live (decisive)

Send a plain test to `probe-$(date +%s)@jorisjonkers.dev` from **two** senders:

| Sender                      | Result                                | Conclusion                                                                           |
| --------------------------- | ------------------------------------- | ------------------------------------------------------------------------------------ |
| Gmail (non-Microsoft)       | Lands in Stalwart catch-all           | MX already on Stalwart                                                               |
| Gmail                       | Microsoft `5.7.x` NDR / never arrives | **Blocker #1**: MX still points to Microsoft                                         |
| Outlook/live.nl (Microsoft) | Microsoft NDR while Gmail works       | **Blocker #2**: domain still an accepted domain in an M365 tenant (internal routing) |

Also check current state:

```sh
dig +short MX jorisjonkers.dev      # expect: 10 mail.jorisjonkers.dev once cut over
dig +short TXT jorisjonkers.dev     # inspect SPF
dig +short A mail.jorisjonkers.dev  # expect: 167.86.79.203
```

If the MX still shows `...mail.protection.outlook.com` → fix Step 1. If the MX is already
Stalwart but Microsoft senders still bounce → you **must** also do Step 2.

## Step 1 — Cloudflare DNS (fixes blocker #1)

All mail records are **DNS-only / grey-cloud** (never proxied), per ADR-003 and
`dns-zone-policy.md`.

| Type    | Name                                     | Value                                                            | Notes                                                                                          |
| ------- | ---------------------------------------- | ---------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| A       | `mail.jorisjonkers.dev`                  | `167.86.79.203`                                                  | Frankfurt VPS; grey-cloud. Already present.                                                    |
| MX      | `jorisjonkers.dev`                       | `10 mail.jorisjonkers.dev`                                       | **Remove** any `*.mail.protection.outlook.com` MX.                                             |
| TXT     | `jorisjonkers.dev`                       | `v=spf1 a:mail.jorisjonkers.dev -all`                            | Replace any `include:spf.protection.outlook.com`. Use `-all` once Stalwart is the only sender. |
| TXT     | `_dmarc.jorisjonkers.dev`                | `v=DMARC1; p=quarantine; rua=mailto:postmaster@jorisjonkers.dev` | Start at `p=quarantine`; tighten to `p=reject` after monitoring.                               |
| TXT     | `<selector>._domainkey.jorisjonkers.dev` | DKIM public key **from Stalwart**                                | See "DKIM" below — do not invent; publish what Stalwart generated.                             |
| TXT     | `_mta-sts.jorisjonkers.dev`              | `v=STSv1; id=<YYYYMMDDNN>`                                       | Optional; only if the MTA-STS policy below is served.                                          |
| CNAME/A | `mta-sts.jorisjonkers.dev`               | host serving `/.well-known/mta-sts.txt`                          | Optional, MTA-STS.                                                                             |

### DKIM

Stalwart signs outbound mail with a DKIM key it generates. Do **not** fabricate the record:

1. In the Stalwart webadmin (or via `stalwart-cli`), read the configured DKIM signature for
   `jorisjonkers.dev` and its **selector** and **public key**.
2. Publish exactly one TXT at `<selector>._domainkey.jorisjonkers.dev` with
   `v=DKIM1; k=rsa; p=<public-key>` (or `k=ed25519` if that is the configured algorithm).
3. Remove any Microsoft `selector1`/`selector2._domainkey` CNAMEs left from M365.

> The Cloudflare API token Stalwart already holds (`CF_DNS_API_TOKEN`,
> `secret/platform/edge` → `cloudflare.dns_api_token`) is scoped to `Zone:DNS:Edit` and is
> used for ACME DNS-01 only; it does not auto-publish MX/SPF/DKIM/DMARC. These are manual.

## Step 2 — Microsoft 365 (fixes blocker #2 — the part people miss)

Repointing DNS is **not enough** if `jorisjonkers.dev` is still claimed by an M365 tenant:
Microsoft-hosted senders route internally and ignore the public MX. To make Stalwart truly
authoritative you must release the domain from the tenant.

1. Sign in to the Microsoft 365 admin center as a tenant admin.
2. **Exchange admin center → Mail flow → Accepted domains**: confirm `jorisjonkers.dev` is
   listed (this is why internal routing happens).
3. Migrate any wanted mail out of the M365 mailboxes (`joris.jonkers@`, `extratoast@`) into
   Stalwart first — removing the domain is disruptive.
4. **Microsoft 365 admin → Settings → Domains → `jorisjonkers.dev` → Remove**, or remove it
   as an accepted domain in Exchange. A domain can be authoritative in exactly one system;
   once Microsoft no longer claims it, Microsoft-origin senders fall back to the public MX
   → Stalwart.

> Interim alternative (not recommended as the end state): set the M365 accepted domain to
> **Internal relay** so Microsoft relays unknown recipients out via MX. This still keeps
> Microsoft as the primary target and requires the public MX to point at Microsoft — the
> opposite of the goal. Prefer full removal.

## Step 3 — Stalwart destination mailbox

Spec 002 declares the catch-all target mailbox (`joris.jonkers`, alias `extratoast`) in
`infra/stalwart/accounts.json`. For the reconcile to actually create/manage it, populate
Vault:

```
secret/platform/mail   key: joris.password   value: <desired mailbox password>
```

If this key is left empty, the reconcile **skips** the account (safe no-op) and the
catch-all will have no Stalwart-managed destination — set it before relying on catch-all
delivery. If `joris.jonkers@` already exists as a manually-created webadmin mailbox, setting
`joris.password` makes the reconcile adopt it and reset its password to the Vault value.

## Step 4 — Verify (post-cutover checklist)

- [ ] `dig +short MX jorisjonkers.dev` → `10 mail.jorisjonkers.dev` only.
- [ ] `dig +short A mail.jorisjonkers.dev` → `167.86.79.203`.
- [ ] SPF TXT authorizes Stalwart, not Microsoft.
- [ ] DKIM TXT at the Stalwart selector resolves and matches the server's public key.
- [ ] Test from **Gmail** to a real mailbox → delivered to Stalwart.
- [ ] Test from **Outlook/live.nl** to `something@jorisjonkers.dev` → delivered to the
      `joris.jonkers` catch-all mailbox, **no** `5.7.520` NDR.
- [ ] Test to `extratoast@jorisjonkers.dev` → same mailbox.
- [ ] Send **outbound** from Stalwart to a Gmail/mail-tester address → SPF + DKIM + DMARC
      all pass (e.g. via mail-tester.com, score 9–10/10).
- [ ] `joris.jonkers` mailbox present after a reconcile against an empty datastore.

## Rollback

- DNS: restore the previous MX/SPF/DKIM records in Cloudflare; mail returns to Microsoft.
- M365: re-add `jorisjonkers.dev` as an accepted domain and re-verify the TXT.
- Stalwart: clearing Vault `joris.password` makes the reconcile stop managing the mailbox
  (it is not deleted).
