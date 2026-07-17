#!/usr/bin/env bash
set -euo pipefail

ODL_HOME="${ODL_HOME:-/opt/opendaylight}"
CLIENT="${ODL_HOME}/bin/client"

wait_for_karaf_ssh() {
    echo "Waiting for Karaf SSH port..."

    for attempt in $(seq 1 120); do
        if timeout 1s bash -c '</dev/tcp/127.0.0.1/8101' 2>/dev/null; then
            echo "Karaf SSH port 8101 is accepting connections."
            return 0
        fi

        echo "Waiting for Karaf SSH port: attempt ${attempt}/120"

        if (( attempt % 5 == 0 )); then
            echo "================ Karaf log tail ================="
            tail -n 100 "${ODL_HOME}/data/log/karaf.log" || true
        fi

        sleep 2
    done

    echo "Karaf SSH port never became reachable."
    cat "${ODL_HOME}/data/log/karaf.log" || true
    return 1
}

run_karaf_batch() {
    local batch_file="$1"

    timeout 1800s "${CLIENT}" \
        -h 127.0.0.1 \
        -a 8101 \
        -u karaf \
        -p karaf \
        -r 1 \
        -d 1 \
        < "${batch_file}"
}

wait_for_karaf_ssh

# Give Karaf extra time after SSH comes up.
echo "Waiting for Karaf services to stabilize..."
sleep 20

BATCH_FILE="$(mktemp)"

cat > "${BATCH_FILE}" <<'KARAF_CMDS'
bundle:install -s wrap:mvn:javax.inject/javax.inject/1$Bundle-SymbolicName=javax.inject&Bundle-Version=1.0.0&Export-Package=javax.inject;version=1.0.0

feature:install odl-restconf-all
feature:install odl-netconf-all

feature:install odl-openflowplugin-flow-services-rest
feature:install odl-openflowplugin-app-table-miss-enforcer
feature:install odl-openflowplugin-nxm-extensions
feature:install odl-openflowplugin-app-lldp-speaker
feature:install odl-openflowplugin-app-topology-lldp-discovery
feature:install odl-openflowplugin-app-topology-manager

feature:install odl-ovsdb-southbound-impl-ui

feature:install odl-bgpcep-rsvp
feature:install odl-bgpcep-pcep
feature:install odl-bgpcep-pcep-topology
feature:install odl-bgpcep-pcep-cli

feature:install odl-bgpcep-graph
feature:install odl-bgpcep-algo

feature:install odl-bgpcep-pcep-server
feature:install odl-bgpcep-pcep-server-provider
feature:install odl-bgpcep-pcep-tunnel-provider

feature:install odl-bgpcep-bgp
feature:install odl-bgpcep-bgp-inet
feature:install odl-bgpcep-bgp-linkstate
feature:install odl-bgpcep-bgp-topology
feature:install odl-bgpcep-bgp-cli

feature:install odl-bgpcep-config-loader-impl
feature:install odl-bgpcep-protocols-config-loader
feature:install odl-bgpcep-topology-config-loader

feature:list -i | grep -E 'restconf|netconf|openflow|ovsdb|pcep|bgpcep|rsvp'
diag
logout
KARAF_CMDS

echo "Running Karaf provisioning batch..."
if ! run_karaf_batch "${BATCH_FILE}"; then
    echo "Karaf batch provisioning failed."
    echo "================ Karaf log tail ================="
    tail -n 200 "${ODL_HOME}/data/log/karaf.log" || true
    rm -f "${BATCH_FILE}"
    exit 1
fi

rm -f "${BATCH_FILE}"

echo "Provisioning complete."