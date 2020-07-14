# Copyright 2018 The Hyve
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM openjdk:8-alpine as builder

# ----
# Install Maven
RUN apk add --no-cache curl tar bash
ARG MAVEN_VERSION=3.3.9
ARG USER_HOME_DIR="/root"
RUN mkdir -p /usr/share/maven && \
    curl -fsSL http://apache.osuosl.org/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar -xzC /usr/share/maven --strip-components=1 && \
    ln -s /usr/share/maven/bin/mvn /usr/bin/mvn
ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"

# Install git needed by build 
RUN apk add git

# Make source folder
RUN mkdir /code /code/kafka-connect-jdbc
WORKDIR /code/kafka-connect-jdbc

# Install maven dependency packages (keep in image)
COPY kafka-connect-jdbc/pom.xml /code/kafka-connect-jdbc

RUN mvn dependency:resolve

COPY kafka-connect-jdbc/src /code/kafka-connect-jdbc/src
COPY kafka-connect-jdbc/checkstyle /code/kafka-connect-jdbc/checkstyle

# Package into JAR
RUN mvn package -DskipTests

WORKDIR /code
RUN mkdir /code/kafka-connect-transform
WORKDIR /code/kafka-connect-transform

COPY kafka-connect-transform/pom.xml /code/kafka-connect-transform

RUN mvn dependency:resolve

COPY kafka-connect-transform/src /code/kafka-connect-transform/src

# Package into JAR
RUN mvn package

WORKDIR /code

FROM confluentinc/cp-kafka-connect-base:5.5.0

MAINTAINER @mpgxvii

LABEL description="Kafka JDBC connector"

ENV CONNECT_PLUGIN_PATH /usr/share/java/kafka-connect/plugins

# To isolate the classpath from the plugin path as recommended
COPY --from=builder /code/kafka-connect-jdbc/target/components/packages/confluentinc-kafka-connect-jdbc-5.5.0/confluentinc-kafka-connect-jdbc-5.5.0/ ${CONNECT_PLUGIN_PATH}/kafka-connect-jdbc/
COPY --from=builder /code/kafka-connect-transform/target/*.jar ${CONNECT_PLUGIN_PATH}/kafka-connect-transform/

# Load topics validator
COPY ./docker/kafka-wait /usr/bin/kafka-wait

# Load modified launcher
COPY ./docker/launch /etc/confluent/docker/launch

# create parent directory for storing offsets in standalone mode
RUN mkdir -p /var/lib/kafka-connect-jdbc/logs
