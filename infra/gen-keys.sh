#!/bin/sh
# Generates the RS256 key pair used to sign (accounts-service) and verify (all services) JWTs.
# Development only: production keys belong in a secret manager, never in the repository.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)/keys"
mkdir -p "$DIR"
if [ -f "$DIR/privateKey.pem" ] && [ -f "$DIR/publicKey.pem" ]; then
  echo "keys already exist in $DIR"
  exit 0
fi
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$DIR/privateKey.pem"
openssl pkey -in "$DIR/privateKey.pem" -pubout -out "$DIR/publicKey.pem"
chmod 644 "$DIR/publicKey.pem" "$DIR/privateKey.pem"
echo "wrote $DIR/privateKey.pem and $DIR/publicKey.pem"
