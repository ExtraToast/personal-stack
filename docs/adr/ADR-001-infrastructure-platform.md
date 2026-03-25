# ADR-001: Infrastructure Platform

## Status

Accepted

## Date

2026-03-25

## Context

We need a hosting platform for a self-hosted private stack (jorisjonkers.dev) running Docker Swarm with multiple JVM
services, Vue frontends, Vault, monitoring, and workflow automation. The platform must be cost-effective, support
cloud-init, and allow future expansion.

## Decision

- **Provider:** Contabo Cloud VPS 20 (6 vCPU, 12 GB RAM, 400 GB SSD, 2 snapshots)
- **OS:** Ubuntu 24.04 LTS
- **Orchestration:** Docker Swarm, single-node initially
- **Provisioning:** Cloud-init for server configuration + Contabo API for automation (no Terraform — accidental node
  creation is expensive)
- **Domain:** jorisjonkers.dev with subdomain-per-service routing

## Consequences

- Single node means no HA — acceptable for a personal/portfolio stack
- Swarm API is available from day one (stacks, secrets, configs, networks)
- Adding nodes later is trivial with Swarm
- Cloud-init scripts must be idempotent and tested
- Contabo API automation scripts live in infra/ directory
- May upgrade to larger VPS or add nodes as load increases
- May switch OS to Debian 13 or Arch in the future — cloud-init compatibility must be maintained
