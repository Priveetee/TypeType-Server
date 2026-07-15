#!/usr/bin/env bash
set -euo pipefail

docker pull ghcr.io/typetype-video/typetype-beta:beta
docker pull ghcr.io/typetype-video/typetype-server-beta:beta
docker pull ghcr.io/typetype-video/typetype-downloader-beta:beta
docker pull ghcr.io/typetype-video/typetype-token:latest

echo "Pulled frontend/server/downloader beta images and token latest image."
