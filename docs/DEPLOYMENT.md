# Deploying Naghma Mix for free

The whole stack runs on one **Oracle Cloud Always Free** Arm VM, under a free **DuckDNS** name with HTTPS from Caddy. GitHub Actions builds and deploys on every push to `main`. Expected cost: zero.

```
push to main ─▶ CI (build + test everything) ─▶ Deploy: build Arm64 images ─▶ ghcr.io
                                                        └─ SSH into the VM ─▶ docker compose pull && up
```

## What you get, and the catch

- Oracle's Always Free Arm allowance is **2 OCPUs and 12 GB RAM** in total (it was halved in mid-2026; older guides still say 24 GB). This setup is sized to fit inside it, with memory limits on every container.
- Arm capacity is often sold out. If instance creation fails with *Out of capacity*, retry later or at a quieter hour; you cannot change your home region afterwards, so pick it carefully at sign-up.
- Oracle can reclaim Always Free instances that sit idle for a long time. A small real user base, or a periodic health check, keeps it active.
- Sign-up asks for a card to verify identity. Always Free resources are not charged; don't upgrade to a paid account unless you want to.

## 1. Create the server

1. Sign up at [cloud.oracle.com](https://cloud.oracle.com) and choose a home region close to your users (for Egypt and the Gulf, a Middle East or European region).
2. **Compute → Instances → Create instance**
   - Image: **Canonical Ubuntu 24.04**
   - Shape: **Ampere → VM.Standard.A1.Flex**, **2 OCPUs, 12 GB memory**
   - Networking: keep "assign a public IPv4 address"
   - SSH keys: paste the public half of a key you create for this server:
     ```bash
     ssh-keygen -t ed25519 -f ~/.ssh/naghma -C naghma-deploy
     cat ~/.ssh/naghma.pub
     ```
   - Boot volume: 100 GB (free up to 200 GB total)
3. Open the web ports: **Networking → Virtual cloud networks → your VCN → Security lists → Default → Add ingress rules**, source `0.0.0.0/0`, TCP, destination ports `80` and `443`.
4. Note the instance's **public IP**.

## 2. Get a free domain

1. Sign in at [duckdns.org](https://www.duckdns.org) with GitHub.
2. Add a subdomain, e.g. `naghma`, and set its IP to the server's public IP.

## 3. Prepare the server (one command)

```bash
ssh -i ~/.ssh/naghma ubuntu@<public-ip>
curl -fsSL https://raw.githubusercontent.com/amohsen98/naghma-mix/main/infra/deploy/bootstrap.sh \
  | sudo DOMAIN=naghma.duckdns.org bash
```

This installs Docker, opens ports 80/443 in the host firewall (Oracle's Ubuntu image blocks them by default), adds swap, clones the repository to `/opt/naghma-mix`, generates the JWT keys and writes `.env` with random secrets. Running it twice is safe.

## 4. Connect GitHub to the server

In **GitHub → naghma-mix → Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `SSH_HOST` | the server's public IP |
| `SSH_USER` | `ubuntu` |
| `SSH_KEY` | the whole private key: `cat ~/.ssh/naghma` |

## 5. Deploy

Push to `main`, or open **Actions → Deploy → Run workflow**. The first run builds five Arm64 images (about 10–15 minutes); later runs reuse the build cache. When it finishes, open `https://naghma.duckdns.org`. Caddy fetches the certificates on the first request, which can take a few seconds.

## Operating it

```bash
cd /opt/naghma-mix
alias dc='docker compose -f docker-compose.yml -f docker-compose.prod.yml'

dc ps                          # what is running
dc logs -f catalog worker      # follow logs
dc up -d --scale worker=3      # more analysis throughput (max 6: the job topic's partitions)
dc exec postgres psql -U naghma -d naghma \
  -c "UPDATE accounts.users SET admin = true WHERE email = 'you@example.com';"   # make an admin
```

**Backups:** the data that matters is in PostgreSQL (audio is deleted after analysis). A daily dump:

```bash
crontab -e
0 3 * * * cd /opt/naghma-mix && docker compose exec -T postgres pg_dump -U naghma naghma | gzip > /home/ubuntu/backup-$(date +\%a).sql.gz
```

That keeps one dump per weekday. Copy them off the server now and then (Oracle Object Storage has a free 20 GB tier).

**Roll back:** every image is also tagged with its commit SHA. On the server, `git checkout <sha> && TAG=<sha> dc up -d --no-build`.

## Moving to a paid server later

Nothing is Oracle-specific except the bootstrap's firewall step. Any Ubuntu VPS with 4 vCPU and 8 GB RAM or more works: point DuckDNS (or your own domain) at it, run the bootstrap and change the `SSH_*` secrets. On an x86 server, change `runs-on: ubuntu-24.04-arm` / `platforms: linux/arm64` in `.github/workflows/deploy.yml` to `ubuntu-latest` / `linux/amd64`.
