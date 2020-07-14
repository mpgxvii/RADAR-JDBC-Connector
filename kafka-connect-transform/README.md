# Introduction

# Transformations

## KeyValueTransform

The KeyValueTransform copies the record key into the record value, and adds a timestamp.

### Configuration

#### Examples

##### Standalone Example

This configuration is used typically along with [standalone mode](http://docs.confluent.io/current/connect/concepts.html#standalone-workers).

```properties
name=Connector1
connector.class=org.apache.kafka.some.SourceConnector
tasks.max=1
transforms=tran
transforms.tran.type=org.radarbase.kafka.connect.transforms.KeyValueTransform
```

##### Distributed Example

This configuration is used typically along with [distributed mode](http://docs.confluent.io/current/connect/concepts.html#distributed-workers).
Write the following json to `connector.json`, configure all of the required values, and use the command below to
post the configuration to one the distributed connect worker(s).

```json
{
  "name": "Connector",
  "connector.class": "org.apache.kafka.some.SourceConnector",
  "transforms": "tran",
  "transforms.tran.type": "org.radarbase.kafka.connect.transforms.KeyValueTransform"
}
```

Use curl to post the configuration to one of the Kafka Connect Workers. Change `http://localhost:8083/` the the endpoint of
one of your Kafka Connect worker(s).

Create a new instance.

```bash
curl -s -X POST -H 'Content-Type: application/json' --data @connector.json http://localhost:8083/connectors
```

Update an existing instance.

```bash
curl -s -X PUT -H 'Content-Type: application/json' --data @connector.json http://localhost:8083/connectors/TestSinkConnector1/config
```
