# Build stage for Java classes
FROM hbpmip/java-base-build:3.6.0-jdk-11-0 as build-java-env

ENV HOME=/root
COPY docker/seed-src /project/docker/seed-src
COPY pom.xml /project/
WORKDIR /project

# Run Maven on an empty project and force it to download most of its dependencies to fill the cache
RUN mkdir -p /usr/share/maven/ref/repository \
    && cp /usr/share/maven/ref/settings-docker.xml /root/.m2/settings.xml \
    && mvn -DSEED=true clean \
        resources:resources \
        compiler:compile \
        surefire:test \
        jar:jar \
        package

COPY src/ /project/src/

# Repeating the file copy works better. I dunno why.
RUN cp /usr/share/maven/ref/settings-docker.xml /root/.m2/settings.xml \
    && mvn clean package

RUN jar cvf target/logback.jar -C src/main/resources logback.xml

# Final image
FROM hbpmip/flyway:5.1.4-0
MAINTAINER Ludovic Claude <ludovic.claude@chuv.ch>

ARG BUILD_DATE
ARG VCS_REF
ARG VERSION

COPY --from=build-java-env /project/target/logback.jar /flyway/lib/
COPY --from=build-java-env /project/target/data-db-setup.jar /flyway/jars/
COPY --from=build-java-env \
        /usr/share/maven/ref/repository/net/sf/supercsv/super-csv/2.4.0/super-csv-2.4.0.jar \
        /usr/share/maven/ref/repository/org/apache/commons/commons-lang3/3.5/commons-lang3-3.5.jar \
        /usr/share/maven/ref/repository/com/github/spullara/mustache/java/compiler/0.9.5/compiler-0.9.5.jar \
        /usr/share/maven/ref/repository/com/fasterxml/jackson/core/jackson-core/2.9.8/jackson-core-2.9.8.jar \
        /usr/share/maven/ref/repository/com/fasterxml/jackson/core/jackson-databind/2.9.8/jackson-databind-2.9.8.jar \
        /usr/share/maven/ref/repository/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar \
        /usr/share/maven/ref/repository/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar \
        /usr/share/maven/ref/repository/ch/qos/logback/logback-core/1.2.3/logback-core-1.2.3.jar \
        /usr/share/maven/ref/repository/ch/qos/logback/logback-classic/1.2.3/logback-classic-1.2.3.jar \
        /flyway/lib/
COPY schemas/* /schemas/
COPY docker/run.sh /

RUN chmod +x /run.sh

ENV FLYWAY_DBMS=postgresql \
    FLYWAY_HOST=db \
    FLYWAY_PORT=5432 \
    FLYWAY_DATABASE_NAME=data \
    FLYWAY_USER=data \
    FLYWAY_PASSWORD=data \
    FLYWAY_SCHEMAS=public

ENV IMAGE="hbpmip/data-db-setup:$VERSION"

WORKDIR /flyway
ENTRYPOINT ["/run.sh"]
CMD ["migrate"]

LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="hbpmip/data-db-setup" \
      org.label-schema.description="Base image to setup features database and fill it with data" \
      org.label-schema.url="https://github.com/LREN-CHUV/data-db-setup" \
      org.label-schema.vcs-type="git" \
      org.label-schema.vcs-url="https://github.com/LREN-CHUV/data-db-setup" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.version="$VERSION" \
      org.label-schema.vendor="LREN CHUV" \
      org.label-schema.license="Apache2.0" \
      org.label-schema.docker.dockerfile="Dockerfile" \
      org.label-schema.schema-version="1.0"
