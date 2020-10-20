FROM openjdk:11-jre-slim

ARG JAR_FILE=census-int-mock-envoy-limiter*.jar
RUN apt-get update
RUN apt-get -yq clean
RUN groupadd -g 989 census-int-mock-envoy-limiter && \
    useradd -r -u 989 -g census-int-mock-envoy-limiter census-int-mock-envoy-limiter
USER census-int-mock-envoy-limiter
COPY target/$JAR_FILE /opt/census-int-mock-envoy-limiter.jar

ENTRYPOINT [ "java", "-jar", "/opt/census-int-mock-envoy-limiter.jar" ]

