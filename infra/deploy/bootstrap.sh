#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu server (written for Oracle Cloud's Always Free Arm VM).
#
#   curl -fsSL https://raw.githubusercontent.com/amohsen98/naghma-mix/main/infra/deploy/bootstrap.sh \
#     | sudo DOMAIN=naghma.duckdns.org bash
#
# Safe to run again: it never overwrites an existing .env or existing keys.
set -euo pipefail

: "${DOMAIN:?Set DOMAIN, for example: sudo DOMAIN=naghma.duckdns.org bash bootstrap.sh}"
REPO="${REPO:-https://github.com/amohsen98/naghma-mix.git}"
DIR="${DIR:-/opt/naghma-mix}"
APP_USER="${SUDO_USER:-ubuntu}"

echo "==> Installing packages"
apt-get update -y
apt-get install -y ca-certificates curl git openssl iptables-persistent

if ! command -v docker >/dev/null; then
  echo "==> Installing Docker"
  curl -fsSL https://get.docker.com | sh
fi
usermod -aG docker "$APP_USER"

echo "==> Opening ports 80 and 443 in the host firewall"
# Oracle's Ubuntu images ship iptables rules that reject everything except SSH.
for port in 80 443; do
  iptables -C INPUT -p tcp --dport "$port" -m state --state NEW -j ACCEPT 2>/dev/null \
    || iptables -I INPUT 6 -p tcp --dport "$port" -m state --state NEW -j ACCEPT
done
netfilter-persistent save

if ! swapon --show | grep -q /swapfile; then
  echo "==> Adding 4 GB swap as headroom"
  fallocate -l 4G /swapfile && chmod 600 /swapfile && mkswap /swapfile && swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "==> Fetching the code into $DIR"
if [ -d "$DIR/.git" ]; then
  git -C "$DIR" pull --ff-only
else
  git clone "$REPO" "$DIR"
fi
cd "$DIR"

sh infra/gen-keys.sh

if [ ! -f .env ]; then
  echo "==> Writing .env with fresh random secrets"
  rand() { openssl rand -hex 24; }
  cat > .env <<EOF
DOMAIN=$DOMAIN
GH_OWNER=amohsen98
POSTGRES_PASSWORD=$(rand)
S3_ACCESS_KEY=naghma$(openssl rand -hex 4)
S3_SECRET_KEY=$(rand)
BILLING_WEBHOOK_SECRET=$(rand)
WORKER_REPLICAS=2
EOF
  chmod 600 .env
fi
chown -R "$APP_USER":"$APP_USER" "$DIR"

cat <<EOF

Server ready. Next:
  1. In GitHub > naghma-mix > Settings > Secrets and variables > Actions, add:
       SSH_HOST = this server's public IP
       SSH_USER = $APP_USER
       SSH_KEY  = the private key that matches this server's authorized key
  2. Push to main (or run the Deploy workflow by hand). The pipeline builds the images
     and starts everything here. Then open https://$DOMAIN
EOF
