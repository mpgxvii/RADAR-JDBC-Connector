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

FROM maven:3.6-jdk-8 as builder

# Make kafka-connect-transform source folder
RUN mkdir /code /code/kafka-connect-transform
WORKDIR /code/kafka-connect-transform

# Install maven dependency packages (keep in image)
COPY kafka-connect-transform/pom.xml /code/kafka-connect-transform
RUN mvn dependency:resolve

# Package into JAR
COPY kafka-connect-transform/src /code/kafka-connect-transform/src
RUN mvn package

# Make kafka-connect-jdbc source folder
WORKDIR /code
RUN mkdir /code/kafka-connect-jdbc
WORKDIR /code/kafka-connect-jdbc

# Install maven dependency packages (keep in image)
COPY kafka-connect-jdbc/pom.xml /code/kafka-connect-jdbc
RUN mvn dependency:resolve

# Package into JAR
COPY kafka-connect-jdbc/src /code/kafka-connect-jdbc/src
RUN mvn package -DskipTests -Dcheckstyle.skip

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
