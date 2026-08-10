FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew -Dorg.gradle.jvmargs="-Xmx1024m -Dfile.encoding=UTF-8" bootJar --no-daemon
RUN find /workspace/build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -exec cp '{}' /workspace/backend.jar \;

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 medassist \
    && useradd --uid 10001 --gid medassist --no-create-home --shell /usr/sbin/nologin medassist \
    && mkdir -p /app \
    && chown -R medassist:medassist /app
COPY --from=build --chown=medassist:medassist /workspace/backend.jar /app/backend.jar
USER 10001:10001
WORKDIR /app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
