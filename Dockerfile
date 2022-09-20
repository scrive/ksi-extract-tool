ARG HOME=/ksi-extract-tool

#Build container
FROM gradle:jdk11 as builder
ARG HOME
WORKDIR $HOME

COPY --chown=gradle:gradle build.gradle.kts settings.gradle.kts $HOME/
COPY --chown=gradle:gradle src $HOME/src
COPY --chown=gradle:gradle lib $HOME/lib
RUN curl -0 https://secure.globalsign.com/cacert/docsignrootr45.crt --output docsignrootr45.crt

RUN gradle build --no-daemon

#Runtime container
FROM amazoncorretto:11-alpine3.15-jdk

ARG ARTIFACT_NAME=ksi-extract-tool-1.0-SNAPSHOT-standalone.jar
ARG HOME
COPY --from=builder $HOME/docsignrootr45.crt docsignrootr45.crt
RUN keytool -importcert -alias guardtime -keystore  /usr/lib/jvm/default-jvm/lib/security/cacerts -storepass changeit -file docsignrootr45.crt -noprompt
COPY --from=builder $HOME/build/libs/$ARTIFACT_NAME app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
