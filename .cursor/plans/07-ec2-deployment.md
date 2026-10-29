# P15 — EC2 deployment (SSH, Nginx, HTTP)

## Objective

Repeatable deploy to AWS EC2 over SSH with Nginx on :80 for school demo (HTTP allowed with documented risk).

## Prerequisites

- P14 local prod-like stack works
- User-provided EC2 host, SSH key path, admin IP — **do not invent**
- Explicit user approval before real remote deploy

## Tasks focus

- `deploy/` Nginx config, remote compose overlay, SSH scripts
- Makefile: `deploy-ec2`, `status-ec2`, `logs-ec2` requiring `EC2_HOST` (fail if missing; never default to localhost)
- Security group guidance: :80 public; SSH restricted; Postgres/Redis not public
- Docs: HTTP demo threat model

## Acceptance criteria

- Scripts validated locally (dry-run / shellcheck / make -n)
- Remote verification only when host approved — evidence includes public IPv4 health URL
- DB/Redis bind private network only

## Verification

```bash
make -n deploy-ec2 EC2_HOST=example
# real: make deploy-ec2 EC2_HOST=... SSH_KEY=...  (with approval)
curl -sS http://$EC2_HOST/api/v1/health
```

## Risks

- No host yet → implementation `DONE` for scripts, remote smoke `BLOCKED`
- Accidental exposure of DB ports — review compose ports before ship

## Phase complete when

Script/Make path verified; remote smoke either evidenced or blocked with clear reason.
