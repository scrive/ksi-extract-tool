#Build container
ARG BUILD_HOME=/ksi-extractor
FROM gradle:jdk11 as builder

ARG BUILD_HOME
ENV APP_HOME=$BUILD_HOME
WORKDIR $APP_HOME
COPY --chown=gradle:gradle build.gradle.kts settings.gradle.kts $APP_HOME/
COPY --chown=gradle:gradle src $APP_HOME/src

RUN gradle build --no-daemon

#Runtime container
FROM openjdk:11-jre-slim

ENV ARTIFACT_NAME=ksi-extract-tool-1.0-SNAPSHOT-standalone.jar
ARG BUILD_HOME
ENV APP_HOME=$BUILD_HOME

COPY --from=builder $APP_HOME/build/libs/$ARTIFACT_NAME app.jar

ENTRYPOINT java -jar app.jar /files/input.pdf
