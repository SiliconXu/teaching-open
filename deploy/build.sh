#!/usr/bin/env bash

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "build db ..."
docker build -t "teaching-open-db:latest" -f "$ROOT_DIR/api/Dockerfile.db" "$ROOT_DIR/api"

echo "build api ..."
docker build -t "teaching-open-api:builder" -f "$ROOT_DIR/api/Dockerfile.builder" "$ROOT_DIR/api"
docker run --rm -v "$ROOT_DIR/api:/workspace" -w /workspace teaching-open-api:builder bash -c "cd /workspace && mvn clean package"
docker build -t "teaching-open-api:latest" -f "$ROOT_DIR/api/Dockerfile" "$ROOT_DIR/api"

echo "build web ..."
docker build -t "teaching-open-web:builder" -f "$ROOT_DIR/web/Dockerfile.builder" "$ROOT_DIR/web"
docker run --rm -v "$ROOT_DIR/web:/workspace" -w /workspace teaching-open-web:builder bash -c "cd /workspace && yarn && yarn build"
docker build -t "teaching-open-web:latest" -f "$ROOT_DIR/web/Dockerfile" "$ROOT_DIR/web"
