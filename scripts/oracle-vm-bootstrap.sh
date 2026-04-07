#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "Run this script with sudo." >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive

echo "Updating apt package index..."
apt-get update

echo "Installing Docker and firewall tools..."
apt-get install -y ca-certificates curl docker.io ufw

echo "Installing Docker Compose plugin..."
if ! apt-get install -y docker-compose-plugin; then
  apt-get install -y docker-compose-v2
fi

echo "Enabling Docker..."
systemctl enable --now docker

if [[ -n "${SUDO_USER:-}" ]]; then
  echo "Adding $SUDO_USER to the docker group..."
  usermod -aG docker "$SUDO_USER"
fi

echo "Configuring UFW for SSH and web traffic..."
ufw allow OpenSSH
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

echo "Bootstrap complete."
echo "Log out and back in before using Docker without sudo."
echo "Remember to also open ports 80 and 443 in Oracle Cloud network security rules."
