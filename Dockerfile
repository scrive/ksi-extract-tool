ARG HOME=/ksi-extract-tool

#Build container
FROM gradle:jdk11 as builder
ARG HOME
WORKDIR $HOME

COPY --chown=gradle:gradle build.gradle.kts settings.gradle.kts $HOME/
COPY --chown=gradle:gradle src $HOME/src

RUN gradle build --no-daemon

#Runtime container
FROM openjdk:11-jre-slim

ARG ARTIFACT_NAME=ksi-extract-tool-1.0-SNAPSHOT-standalone.jar
ARG HOME


COPY --from=builder $HOME/build/libs/$ARTIFACT_NAME app.jar

ENTRYPOINT ["java", "-jar", "app.jar", "/files/"]
