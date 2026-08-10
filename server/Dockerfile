FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew -Dorg.gradle.jvmargs="-Xmx1024m -Dfile.encoding=UTF-8" \
    :server:bootJar --no-daemon --configure-on-demand
RUN find /workspace/server/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -exec cp '{}' /workspace/server.jar \;

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 medassist \
    && useradd --uid 10001 --gid medassist --no-create-home --shell /usr/sbin/nologin medassist \
    && mkdir -p /app /var/lib/medassist/db /var/lib/medassist/recordings \
    && chown -R medassist:medassist /app /var/lib/medassist
COPY --from=build --chown=medassist:medassist /workspace/server.jar /app/server.jar
USER 10001:10001
WORKDIR /app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/server.jar"]
