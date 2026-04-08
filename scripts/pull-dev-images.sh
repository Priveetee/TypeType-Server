#!/usr/bin/env bash
set -euo pipefail

docker pull ghcr.io/priveetee/typetype-beta:beta
docker pull ghcr.io/priveetee/typetype-server-beta:beta
docker pull ghcr.io/priveetee/typetype-downloader-beta:beta
docker pull ghcr.io/priveetee/typetype-token:latest

echo "Pulled frontend/server/downloader beta images and token latest image."
