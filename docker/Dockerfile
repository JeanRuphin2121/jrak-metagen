# =============================================================
# Metagen Server — Multi-stage Docker build
#
# Stage 1 (builder): compiles the Maven project
# Stage 2 (runtime): minimal JRE image with the fat JAR
# =============================================================

# ── Stage 1: Build ────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

# Copy Maven wrapper and POMs first (better layer caching)
COPY mvnw .
COPY .mvn/ .mvn/
COPY pom.xml .
COPY metagen-core/pom.xml   metagen-core/pom.xml
COPY metagen-engine/pom.xml metagen-engine/pom.xml
COPY metagen-server/pom.xml metagen-server/pom.xml

# Download dependencies (cached unless POMs change)
RUN ./mvnw dependency:go-offline -q

# Copy source code
COPY metagen-core/src   metagen-core/src
COPY metagen-engine/src metagen-engine/src
COPY metagen-server/src metagen-server/src

# Build — skip tests (CI runs them separately)
RUN ./mvnw package -pl metagen-server -am -DskipTests -q

# Extract layered JAR for better Docker layer caching
RUN java -Djarmode=layertools \
    -jar metagen-server/target/metagen-server-*.jar \
    extract --destination metagen-server/target/extracted


# ── Stage 2: Runtime ──────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Non-root user for security
RUN addgroup -S metagen && adduser -S metagen -G metagen
USER metagen

WORKDIR /app

# Copy layered JAR (most stable layers first = fewer cache misses)
COPY --from=builder /workspace/metagen-server/target/extracted/dependencies/          ./
COPY --from=builder /workspace/metagen-server/target/extracted/spring-boot-loader/    ./
COPY --from=builder /workspace/metagen-server/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/metagen-server/target/extracted/application/           ./

EXPOSE 8080

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "org.springframework.boot.loader.launch.JarLauncher"]
