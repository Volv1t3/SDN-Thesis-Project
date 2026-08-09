# ------------------------------------------------------------------------------
# Build SDN-MPLS-ML OpenDaylight controller-side application
# ------------------------------------------------------------------------------

FROM maven:3.9.9-eclipse-temurin-21 AS sma-odl-csa-builder

WORKDIR /build/sdn-mpls-ml-odl-cs-app

COPY sdn-mpls-ml-odl-cs-app/ ./

RUN mvn clean install -Pq -DskipTests

FROM eclipse-temurin:21-jre-jammy

# ------------------------------------------------------------------------------
# OpenDaylight version and download source
# ------------------------------------------------------------------------------

ARG ODL_VERSION=0.23.1
ARG ODL_REPOSITORY=https://nexus.opendaylight.org/content/repositories/opendaylight.release/org/opendaylight/integration/karaf
ARG ODL_ARCHIVE=karaf-${ODL_VERSION}.tar.gz
ARG ODL_DOWNLOAD_URL=${ODL_REPOSITORY}/${ODL_VERSION}/${ODL_ARCHIVE}

# ------------------------------------------------------------------------------
# OpenDaylight / Karaf environment
# ------------------------------------------------------------------------------

ENV ODL_HOME=/opt/opendaylight
ENV KARAF_HOME=/opt/opendaylight
ENV KARAF_BASE=/opt/opendaylight

# Higher heap allowance for the full SDN-MPLS-ML controller baseline.
ENV JAVA_MIN_MEM=4G
ENV JAVA_MAX_MEM=6G

ENV JAVA_OPTS="-Djava.awt.headless=true \
-Dfile.encoding=UTF-8 \
-Djava.security.egd=file:/dev/./urandom"

# ------------------------------------------------------------------------------
# Runtime tools
# ------------------------------------------------------------------------------

RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        bash \
        ca-certificates \
        coreutils \
        curl \
        gzip \
        iperf3 \
        iproute2 \
        iputils-ping \
        net-tools \
        netcat-openbsd \
        openssh-server \
        procps \
        python3 \
        python3-pip \
        sudo \
        tar \
        tcpdump \
        tini \
        vim \
        wget \
    && rm -rf /var/lib/apt/lists/*

# ------------------------------------------------------------------------------
# Download and extract OpenDaylight
# ------------------------------------------------------------------------------

WORKDIR /tmp

RUN set -eux; \
    mkdir -p "${ODL_HOME}"; \
    curl \
        --fail \
        --location \
        --retry 5 \
        --retry-delay 2 \
        --output "${ODL_ARCHIVE}" \
        "${ODL_DOWNLOAD_URL}"; \
    tar \
        --extract \
        --gzip \
        --file "${ODL_ARCHIVE}" \
        --directory "${ODL_HOME}" \
        --strip-components=1; \
    rm -f "${ODL_ARCHIVE}"

# ------------------------------------------------------------------------------
# Install generated SDN-MPLS-ML application artifacts into Karaf local repository
# ------------------------------------------------------------------------------

ARG SDN_MPLS_ML_APP_VERSION=0.1.0-SNAPSHOT

RUN mkdir -p \
    "${ODL_HOME}/system/com/sma/sdn/sdn-mpls-ml-cs-app-impl/${SDN_MPLS_ML_APP_VERSION}" \
    "${ODL_HOME}/system/com/sma/sdn/sdn-mpls-ml-cs-app-features/${SDN_MPLS_ML_APP_VERSION}"

COPY --from=sma-odl-csa-builder \
    /build/sdn-mpls-ml-odl-cs-app/impl/target/sdn-mpls-ml-cs-app-impl-${SDN_MPLS_ML_APP_VERSION}.jar \
    ${ODL_HOME}/system/com/sma/sdn/sdn-mpls-ml-cs-app-impl/${SDN_MPLS_ML_APP_VERSION}/sdn-mpls-ml-cs-app-impl-${SDN_MPLS_ML_APP_VERSION}.jar

COPY --from=sma-odl-csa-builder \
    /build/sdn-mpls-ml-odl-cs-app/impl/pom.xml \
    ${ODL_HOME}/system/com/sma/sdn/sdn-mpls-ml-cs-app-impl/${SDN_MPLS_ML_APP_VERSION}/sdn-mpls-ml-cs-app-impl-${SDN_MPLS_ML_APP_VERSION}.pom

COPY --from=sma-odl-csa-builder \
    /build/sdn-mpls-ml-odl-cs-app/features/target/feature/feature.xml \
    ${ODL_HOME}/system/com/sma/sdn/sdn-mpls-ml-cs-app-features/${SDN_MPLS_ML_APP_VERSION}/sdn-mpls-ml-cs-app-features-${SDN_MPLS_ML_APP_VERSION}-features.xml

COPY --from=sma-odl-csa-builder \
    /build/sdn-mpls-ml-odl-cs-app/features/pom.xml \
    ${ODL_HOME}/system/com/sma/sdn/sdn-mpls-ml-cs-app-features/${SDN_MPLS_ML_APP_VERSION}/sdn-mpls-ml-cs-app-features-${SDN_MPLS_ML_APP_VERSION}.pom


# ------------------------------------------------------------------------------
# Maven repositories for Karaf / Pax URL
#
# Keep OpenDaylight Nexus repositories for ODL artifacts and add Maven Central
# for third-party artifacts.
# ------------------------------------------------------------------------------

RUN sed -i \
    's|^org.ops4j.pax.url.mvn.repositories=.*|org.ops4j.pax.url.mvn.repositories=https://nexus.opendaylight.org/content/repositories/opendaylight.release@id=opendaylight.release,https://nexus.opendaylight.org/content/repositories/public@id=opendaylight.public,https://repo1.maven.org/maven2@id=central|' \
    "${ODL_HOME}/etc/org.ops4j.pax.url.mvn.cfg"

# ------------------------------------------------------------------------------
# OpenDaylight features to install automatically at Karaf boot
# ------------------------------------------------------------------------------

ARG ODL_BOOT_FEATURES="odl-restconf-all,odl-netconf-all,odl-netconf-topology,odl-netconf-clustered-topology,odl-restconf-nb,odl-openflowplugin-flow-services-rest,odl-openflowplugin-app-table-miss-enforcer,odl-openflowplugin-nxm-extensions,odl-openflowplugin-app-lldp-speaker,odl-openflowplugin-app-topology-lldp-discovery,odl-openflowplugin-app-topology-manager,odl-ovsdb-southbound-impl-ui,odl-bgpcep-rsvp,odl-bgpcep-pcep,odl-bgpcep-pcep-topology,odl-bgpcep-pcep-cli,odl-bgpcep-graph,odl-bgpcep-algo,odl-bgpcep-pcep-server,odl-bgpcep-pcep-server-provider,odl-bgpcep-pcep-tunnel-provider,odl-bgpcep-bgp,odl-bgpcep-bgp-inet,odl-bgpcep-bgp-linkstate,odl-bgpcep-bgp-topology,odl-bgpcep-bgp-cli,odl-bgpcep-config-loader-impl,odl-bgpcep-protocols-config-loader,odl-bgpcep-topology-config-loader"

ARG SDN_MPLS_ML_APP_FEATURE_REPO="mvn:com.sma.sdn/sdn-mpls-ml-cs-app-features/0.1.0-SNAPSHOT/xml/features"
ARG SDN_MPLS_ML_APP_FEATURE_NAME="sdn-mpls-ml-cs-app-features"

RUN python3 - <<'PY'
import os
from pathlib import Path

cfg_path = Path(os.environ["ODL_HOME"]) / "etc" / "org.apache.karaf.features.cfg"

odl_boot_features = [
    feature.strip()
    for feature in os.environ["ODL_BOOT_FEATURES"].split(",")
    if feature.strip()
]

app_feature_repo = os.environ["SDN_MPLS_ML_APP_FEATURE_REPO"].strip()
app_feature_name = os.environ["SDN_MPLS_ML_APP_FEATURE_NAME"].strip()

lines = cfg_path.read_text(encoding="utf-8").splitlines()


def replace_multiline_property(lines, property_name, extra_values):
    start = None
    end = None

    for index, line in enumerate(lines):
        if line.strip().startswith(property_name):
            start = index
            end = index

            while lines[end].rstrip().endswith("\\") and end + 1 < len(lines):
                end += 1

            break

    if start is None:
        raise RuntimeError(f"Could not find {property_name} in {cfg_path}")

    raw = " ".join(line.rstrip("\\").strip() for line in lines[start:end + 1])
    _, value = raw.split("=", 1)

    existing_values = [
        item.strip()
        for item in value.split(",")
        if item.strip()
    ]

    merged = []
    seen = set()

    for item in existing_values + extra_values:
        if item and item not in seen:
            merged.append(item)
            seen.add(item)

    replacement = [f"{property_name} = \\"]
    for index, item in enumerate(merged):
        suffix = "," if index < len(merged) - 1 else ""
        continuation = " \\" if index < len(merged) - 1 else ""
        replacement.append(f"    {item}{suffix}{continuation}")

    return lines[:start] + replacement + lines[end + 1:]


# Equivalent to feature:repo-add for the generated SDN-MPLS-ML feature repo.
lines = replace_multiline_property(
    lines,
    "featuresRepositories",
    [app_feature_repo],
)

# Equivalent to installing the ODL platform features and then the app feature.
# The app feature is deliberately appended last.
lines = replace_multiline_property(
    lines,
    "featuresBoot",
    odl_boot_features + [app_feature_name],
)

cfg_path.write_text("\n".join(lines) + "\n", encoding="utf-8")

print("Updated featuresRepositories with:")
print(app_feature_repo)
print()
print("Appended boot feature:")
print(app_feature_name)
PY

# ------------------------------------------------------------------------------
# Runtime working directory
# ------------------------------------------------------------------------------

WORKDIR /opt/opendaylight

# ------------------------------------------------------------------------------
# Exposed controller and application ports
# ------------------------------------------------------------------------------

# Karaf SSH / client
EXPOSE 8101/tcp

# RESTCONF / HTTP northbound
EXPOSE 8181/tcp

# OpenFlow southbound
EXPOSE 6653/tcp
EXPOSE 6633/tcp

# OVSDB southbound
EXPOSE 6640/tcp

# BGP / BGP-LS
EXPOSE 179/tcp

# PCEP
EXPOSE 4189/tcp

# Additional controller / protocol ports reserved by the demonstrator
EXPOSE 2830/tcp
EXPOSE 4334/tcp
EXPOSE 4335/tcp
EXPOSE 8182/tcp

# SDN-MPLS-ML application Prometheus metrics endpoint
EXPOSE 35762/tcp

# ------------------------------------------------------------------------------
# Healthcheck
# ------------------------------------------------------------------------------

HEALTHCHECK \
    --interval=30s \
    --timeout=15s \
    --start-period=420s \
    --retries=10 \
    CMD ./bin/client -r 1 -d 5 "logout" >/dev/null 2>&1 || exit 1

# ------------------------------------------------------------------------------
# Entrypoint
# ------------------------------------------------------------------------------

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["./bin/karaf", "server"]