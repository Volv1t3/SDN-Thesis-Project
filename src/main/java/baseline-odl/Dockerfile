FROM eclipse-temurin:21-jre-jammy

# OpenDaylight version and download source.
ARG ODL_VERSION=0.23.1
ARG ODL_REPOSITORY=https://nexus.opendaylight.org/content/repositories/opendaylight.release/org/opendaylight/integration/karaf
ARG ODL_ARCHIVE=karaf-${ODL_VERSION}.tar.gz
ARG ODL_DOWNLOAD_URL=${ODL_REPOSITORY}/${ODL_VERSION}/${ODL_ARCHIVE}

# OpenDaylight/Karaf environment.
ENV ODL_HOME=/opt/opendaylight
ENV KARAF_HOME=/opt/opendaylight
ENV KARAF_BASE=/opt/opendaylight
ENV JAVA_MIN_MEM=1G
ENV JAVA_MAX_MEM=4G
ENV JAVA_OPTS="-Djava.awt.headless=true \
-Dfile.encoding=UTF-8 \
-Djava.security.egd=file:/dev/./urandom"

# Install required runtime tools.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        bash \
        ca-certificates \
        curl \
        tini \
        tar \
        gzip \
        procps \
        coreutils \
        iproute2 \
        iputils-ping \
        net-tools \
        curl \
        wget \
        tcpdump \
        iperf3 \
        netcat-openbsd \
        openssh-server \
        python3 \
        python3-pip \
        vim \
        procps \
        ca-certificates \
        sudo \
    && rm -rf /var/lib/apt/lists/*

# Download and extract ODL.
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

# Enable Maven Central for Karaf/Pax URL.
RUN sed -i \
    's|^org.ops4j.pax.url.mvn.repositories=.*|org.ops4j.pax.url.mvn.repositories=https://repo1.maven.org/maven2@id=central|' \
    "${ODL_HOME}/etc/org.ops4j.pax.url.mvn.cfg"

# Copy provisioning script into the image.
COPY install-odl-features.sh /usr/local/bin/install-odl-features.sh
RUN chmod 0755 /usr/local/bin/install-odl-features.sh

WORKDIR /opt/opendaylight

# Exposed controller ports.
EXPOSE 8101/tcp
EXPOSE 8181/tcp
EXPOSE 6653/tcp
EXPOSE 6633/tcp
EXPOSE 6640/tcp
EXPOSE 4189/tcp
EXPOSE 179/tcp
EXPOSE 2830/tcp
EXPOSE 4334/tcp
EXPOSE 4335/tcp
EXPOSE 8182/tcp

# Healthcheck only verifies Karaf client connectivity.
HEALTHCHECK \
    --interval=30s \
    --timeout=15s \
    --start-period=180s \
    --retries=10 \
    CMD ./bin/client -r 1 -d 5 "logout" >/dev/null 2>&1 || exit 1

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["./bin/karaf", "server"]