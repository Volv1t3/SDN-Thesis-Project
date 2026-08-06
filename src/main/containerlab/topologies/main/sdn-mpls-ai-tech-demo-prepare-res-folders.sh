#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  prepare-containerlab-data-dirs.sh <DATA_DIR>

Description:
  Prepares existing Containerlab service data folders with deterministic
  ownership and permissions for Grafana, Prometheus, and the SDNFlow API.

Expected folders under DATA_DIR:
  grafana-data/
  prometheus-data/
  ml-api-logs/

Ownership convention:
  Grafana:    472:472
  Prometheus: 65534:65534
  API:        10001:10001

Example:
  ./prepare-containerlab-data-dirs.sh ../../data-folders
EOF
}

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

require_directory() {
  local path="$1"

  if [[ ! -d "${path}" ]]; then
    fail "Required directory does not exist: ${path}"
  fi
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

if [[ $# -ne 1 ]]; then
  usage >&2
  exit 2
fi

DATA_DIR="$1"

if [[ ! -d "${DATA_DIR}" ]]; then
  fail "DATA_DIR does not exist: ${DATA_DIR}"
fi

DATA_DIR="$(cd "${DATA_DIR}" && pwd)"

GRAFANA_DATA_DIR="${DATA_DIR}/grafana-data"
PROMETHEUS_DATA_DIR="${DATA_DIR}/prometheus-data"
ML_API_LOGS_DIR="${DATA_DIR}/ml-api-logs"

require_directory "${GRAFANA_DATA_DIR}"
require_directory "${PROMETHEUS_DATA_DIR}"
require_directory "${ML_API_LOGS_DIR}"

echo "Preparing Containerlab writable data directories under:"
echo "  ${DATA_DIR}"
echo

echo "Applying Grafana ownership: 472:472"
sudo chown -R 472:472 "${GRAFANA_DATA_DIR}"
sudo chmod -R u+rwX,g+rwX,o-rwx "${GRAFANA_DATA_DIR}"

echo "Applying Prometheus ownership: 65534:65534"
sudo chown -R 65534:65534 "${PROMETHEUS_DATA_DIR}"
sudo chmod -R u+rwX,g+rwX,o-rwx "${PROMETHEUS_DATA_DIR}"

echo "Applying SDNFlow API ownership: 10001:10001"
sudo chown -R 10001:10001 "${ML_API_LOGS_DIR}"
sudo chmod -R u+rwX,g+rwX,o-rwx "${ML_API_LOGS_DIR}"

echo "Applying ACL Configurations For Read Access to Mounted Folders" 
sudo setfacl -R -m u:santiago-arellano:rx "${GRAFANA_DATA_DIR}"
sudo setfacl -R -m u:santiago-arellano:rx "${PROMETHEUS_DATA_DIR}"
sudo setfacl -R -m u:santiago-arellano:rwx "${ML_API_LOGS_DIR}"

sudo setfacl -R -d -m u:santiago-arellano:rx "${GRAFANA_DATA_DIR}"
sudo setfacl -R -d -m u:santiago-arellano:rx "${PROMETHEUS_DATA_DIR}"
sudo setfacl -R -d -m u:santiago-arellano:rwx "${ML_API_LOGS_DIR}"

echo
echo "Final directory state:"
ls -ld \
  "${GRAFANA_DATA_DIR}" \
  "${PROMETHEUS_DATA_DIR}" \
  "${ML_API_LOGS_DIR}"

echo
echo "Done."