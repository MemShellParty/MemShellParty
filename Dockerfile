FROM --platform=$BUILDPLATFORM  buildpack-deps:bullseye-scm AS source

WORKDIR /usr/src

RUN git clone --depth 1 https://github.com/MemShellParty/MemShellParty.git . && \
    rm -rf vul integration-test tools

# https://hub.docker.com/r/oven/bun
FROM --platform=$BUILDPLATFORM oven/bun:1.4.0 AS frontend

ARG ROUTE_ROOT_PATH=""
ARG CONTEXT_PATH=""

WORKDIR /usr/src/web

ENV VITE_APP_API_URL=${CONTEXT_PATH} \
    VITE_APP_BASE_PATH=${ROUTE_ROOT_PATH}/ui

COPY --from=source /usr/src/web/package.json /usr/src/web/bun.lock /usr/src/web/
COPY --from=source /usr/src/web/vendor/cfr /usr/src/web/vendor/cfr

RUN bun install --frozen-lockfile

COPY --from=source /usr/src/web /usr/src/web

RUN bun run build

# Gradle 发行版随官方镜像提供：services.gradle.org 会 307 到 GitHub Releases，
# 构建时在线下载偶发 504/超时。版本需与 gradle/wrapper/gradle-wrapper.properties 一致。
FROM --platform=$BUILDPLATFORM gradle:8.14.6-jdk21 AS gradle-dist

# https://hub.docker.com/_/eclipse-temurin/tags?name=21.
FROM --platform=$BUILDPLATFORM  eclipse-temurin:21.0.12_8-jdk-noble AS backend

WORKDIR /usr/src

COPY --from=source /usr/src /usr/src

COPY --from=gradle-dist /opt/gradle /opt/gradle

COPY --from=frontend /usr/src/memshell-party-boot/src/main/resources /usr/src/memshell-party-boot/src/main/resources

RUN /opt/gradle/bin/gradle :memshell-party-boot:bootjar -x test

FROM eclipse-temurin:21.0.12_8-jre-noble

LABEL authors="ReaJason<reajason1225@gmail.com>"

WORKDIR /app

RUN groupadd -r spring && \
    useradd -r -g spring spring

COPY --from=backend --chown=spring:spring /usr/src/memshell-party-boot/build/libs/*.jar app.jar

USER spring:spring

ENV INTERNAL_JAVA_OPTS="\
    -Djava.security.egd=file:/dev/./urandom -Dfastison.parser.safeMode=true \
    --add-opens=java.base/java.util=ALL-UNNAMED \
    --add-opens=java.xml/com.sun.org.apache.xalan.internal.xsltc.trax=ALL-UNNAMED \
    --add-opens=java.xml/com.sun.org.apache.xalan.internal.xsltc.runtime=ALL-UNNAMED"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS $INTERNAL_JAVA_OPTS -jar app.jar $BOOT_OPTS"]
