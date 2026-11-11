# EB-76 / P6-05: Docker/runtime hardening.
# Base images are digest-pinned (verified against the Docker Hub registry API and
# cross-checked with `docker manifest inspect`). Bumping a digest = deliberate
# maintenance step (re-run the docker smoke workflow); a pinned digest does not
# float, so CVE patches require this manual bump.
FROM maven@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin@sha256:194729a50fcca3b498180858b54cf1b2e3816208cf8d8a1fca9856635f72ca8a
WORKDIR /app
# Dedicated non-root runtime identity (uid/gid 1001). Only the app-owned writable
# directory is chowned; /tmp is world-writable by base-image default. Shell kept
# for operational debugging; nologin login shell prevents interactive logins.
RUN groupadd --system --gid 1001 edunest \
    && useradd --system --uid 1001 --gid 1001 --home-dir /app --shell /usr/sbin/nologin edunest \
    && mkdir -p /app/uploads \
    && chown -R edunest:edunest /app/uploads
COPY --from=build /app/target/*.jar app.jar
# curl exists only for the HEALTHCHECK below (Temurin JRE ships neither curl nor
# wget); kept minimal, apt lists cleared so no package cache bloats a layer.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
# Docker HEALTHCHECK probes the process-level liveness endpoint only (db/mail
# indicators are disabled by EB-79, show-details=never) — it cannot fail on a
# transient DB blip and exposes no health detail. Railway ignores Docker
# HEALTHCHECK; its own restart policy is unaffected by this line.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 CMD curl -fsS "http://127.0.0.1:${PORT:-8080}/actuator/health/liveness" || exit 1
USER edunest
# Exec-form: SIGTERM is delivered to the JVM (PID 1) directly; Spring Boot
# handles it via shutdown hooks (graceful drain configured server-side).
ENTRYPOINT ["java", "-jar", "app.jar"]
