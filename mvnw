#!/bin/sh
set -eu
MVNW_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MVNW_VERSION=3.9.11
MVNW_ARCHIVE="apache-maven-${MVNW_VERSION}-bin.tar.gz"
MVNW_BASE="${MAVEN_USER_HOME:-${HOME}/.m2}/wrapper/dists/apache-maven-${MVNW_VERSION}"
MVNW_HOME="${MVNW_BASE}/apache-maven-${MVNW_VERSION}"
if [ ! -x "${MVNW_HOME}/bin/mvn" ]; then
  mkdir -p "${MVNW_BASE}"
  curl -fsSL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MVNW_VERSION}/${MVNW_ARCHIVE}" -o "${MVNW_BASE}/${MVNW_ARCHIVE}"
  tar -xzf "${MVNW_BASE}/${MVNW_ARCHIVE}" -C "${MVNW_BASE}"
fi
exec "${MVNW_HOME}/bin/mvn" -f "${MVNW_DIR}/pom.xml" "$@"
