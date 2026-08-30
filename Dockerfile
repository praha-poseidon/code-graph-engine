FROM node:22-bookworm-slim AS frontend-build
WORKDIR /source/code-graph-app/frontend
COPY code-graph-app/frontend/package.json code-graph-app/frontend/package-lock.json ./
RUN npm ci
COPY code-graph-app/frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS application-build
WORKDIR /source
COPY . ./
COPY --from=frontend-build /source/code-graph-app/src/main/resources/static/ ./code-graph-app/src/main/resources/static/
RUN mvn -pl code-graph-app -am -DskipTests package

# CentOS 8.2 is retained because the distributable explicitly targets that base.
# It reached EOL in 2021; use only for compatibility-bound deployments.
FROM centos:8.2.2004
LABEL org.opencontainers.image.title="Code Graph Workbench" \
      org.opencontainers.image.base.name="centos:8.2.2004" \
      org.opencontainers.image.description="Code graph workbench application; database services are supplied by compose"

RUN sed -i -e 's|^mirrorlist=|#mirrorlist=|g' \
           -e 's|^#baseurl=http://mirror.centos.org/\$contentdir/\$releasever|baseurl=https://vault.centos.org/8.2.2004|g' \
           /etc/yum.repos.d/CentOS-*.repo \
    && dnf -y install git openssh-clients maven curl ca-certificates \
    && dnf clean all \
    && rm -rf /var/cache/dnf

ENV JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:/opt/codegraph/parsers/bin:${PATH} \
    CODEGRAPH_WORKSPACE_ROOT=/var/lib/codegraph/workspaces

COPY --from=application-build /opt/java/openjdk /opt/java/openjdk
COPY --from=application-build /source/code-graph-app/target/code-graph-app-0.0.1-SNAPSHOT.jar /opt/codegraph/code-graph-app.jar

RUN useradd --system --uid 10001 --home-dir /var/lib/codegraph --create-home codegraph \
    && mkdir -p /opt/codegraph/parsers/bin /var/lib/codegraph/workspaces \
    && chown -R codegraph:codegraph /opt/codegraph /var/lib/codegraph

USER codegraph
WORKDIR /var/lib/codegraph
EXPOSE 8084
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=10 \
  CMD curl --fail --silent http://localhost:8084/api/code-graph/health-check >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/opt/codegraph/code-graph-app.jar"]
