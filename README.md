# RADAR TimescaleDB Sink Connector and JDBC Connector

This project is based on Confluent's Kafka JDBC connector with additional functionalities, namely:

1. Support for `TimescaleDB` databases
2. Support for multiple `createTable` statements.
3. Support for schema creation and setting of schema name format in the connector config.
4. Support for `TIMESTAMPTZ` data type in `PostgreSQL` databases.

## Connect Single Message Transform

This project also has a transform plugin that transforms the Kafka record before it is written to the database. This KeyValue transform plugin copies the record key fields and values to the record value so that they are included in the data written to the database. This plugin also converts time fields in epoch to Date objects (to prepare them for conversion into `TIMESTAMPTZ` in the connector later).

## TimescaleDB Sink Connector

### Installation

This repository relies on a recent version of docker and docker-compose as well as an installation
of Java 8 or later.

### Usage

Copy `docker/sink-timescale.properties.template` to `docker/sink-timescale.properties` and enter your database connection URL, username, and password.

Now you can run a full Kafka stack using

```shell
docker-compose up -d --build
```

## Contributing

Code should be formatted using the [Google Java Code Style Guide](https://google.github.io/styleguide/javaguide.html).
If you want to contribute a feature or fix browse our [issues](https://github.com/RADAR-base/RADAR-REST-Connector/issues), and please make a pull request.
