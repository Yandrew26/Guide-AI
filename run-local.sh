#!/usr/bin/env bash
# Starts every service from source plus the frontend. Logs go to ./logs; Ctrl+C stops everything.
# Needs: .env, MySQL + Redis running, `docker compose up -d` (Elasticsearch + Neo4j), uvx on PATH.
set -euo pipefail
cd "$(dirname "$0")"
[ -f .env ] || { echo "Missing .env: cp .env.example .env and fill it in." >&2; exit 1; }
mkdir -p logs
trap 'trap - EXIT; kill 0 2>/dev/null' INT TERM EXIT

start() { mvn -q -pl "$1" spring-boot:run >"logs/$2.log" 2>&1 & echo "starting $2 (logs/$2.log)"; }
start multi-agent-service/multi-agent-service-auth   auth
start multi-agent-service/multi-agent-service-search search
start multi-agent-service/multi-agent-service-upload upload
start multi-agent-gateway                            gateway
start multi-agent-gateway-openapi                    gateway-openapi

[ -d multi-agent-frontend/node_modules ] || npm --prefix multi-agent-frontend install
npm --prefix multi-agent-frontend run dev >logs/frontend.log 2>&1 &
echo "frontend: http://localhost:5173 (services take ~30s; watch logs/)"
wait
