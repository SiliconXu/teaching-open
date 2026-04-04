ARG BASE_IMAGE=maven:3.8-openjdk-8-slim
FROM ${BASE_IMAGE}

RUN mkdir -p /root/.m2
COPY settings.xml /root/.m2/settings.xml
