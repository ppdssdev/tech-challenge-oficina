#!/usr/bin/env bash
# KUBECTL must name one executable. Run the caller with sudo on the K3s host.
set -euo pipefail
exec "${K3S:-/usr/local/bin/k3s}" kubectl "$@"
