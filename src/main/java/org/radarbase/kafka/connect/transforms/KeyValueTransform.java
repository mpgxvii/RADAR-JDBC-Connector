/**
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.radarbase.kafka.connect.transforms;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.*;
import org.apache.kafka.connect.transforms.Transformation;
import static org.apache.kafka.connect.transforms.util.Requirements.requireMap;
import static org.apache.kafka.connect.transforms.util.Requirements.requireStruct;

import java.util.*;
import java.util.Date;

/**
 *
 *
 * This transforms records by copying the record key into the record value.
 */
public class KeyValueTransform<R extends ConnectRecord<R>> implements Transformation<R> {
  private static final String PURPOSE = "copying fields from key to value";
  private static final String TIMESTAMP_FIELD = "timestamp";
  private static final String KEYVALUE_SCHEMA_NAME = "KeyToValue";
  private static final Set<String> TIME_FIELDS = new HashSet<>(Arrays.asList("time", "timeReceived", "timeCompleted"));

  @Override
  public R apply(R r) {
    if (r.valueSchema() == null) {
      return applySchemaless(r);
    } else {
      return applyWithSchema(r);
    }
  }

  private R applyWithSchema(R r) {
    SchemaBuilder schemaBuilder = SchemaBuilder.struct().name(KEYVALUE_SCHEMA_NAME);
    schemaBuilder = updateSchema(schemaBuilder, r.keySchema());
    schemaBuilder = updateSchema(schemaBuilder, r.valueSchema());
    schemaBuilder.field(TIMESTAMP_FIELD, Schema.INT64_SCHEMA);
    Schema schema = schemaBuilder.build();

    final Struct recordValue = requireStruct(r.value(), PURPOSE);
    final Struct recordKey = requireStruct(r.key(), PURPOSE);
    Struct newData = new Struct(schema);
    newData = addValuesToNewSchema(newData, recordKey, r.keySchema());
    newData = addValuesToNewSchema(newData, recordValue, r.valueSchema());
    newData.put(TIMESTAMP_FIELD, r.timestamp());

    return r.newRecord(r.topic(), r.kafkaPartition(), r.keySchema(), r.key(), schema, newData, r.timestamp());
  }

  private Struct addValuesToNewSchema(Struct newData, Struct oldData, Schema oldSchema){
    for (Field field: oldSchema.fields()) {
      String fieldName = field.name();
      Object fieldVal = oldData.get(field);
      if(TIME_FIELDS.contains(fieldName) && field.schema().type() == Schema.Type.FLOAT64)
        fieldVal = convertTimestamp(fieldVal);
      newData.put(fieldName, fieldVal);
    }
    return newData;
  }


  private SchemaBuilder updateSchema(SchemaBuilder schemaBuilder, Schema oldSchema){
    for (Field field: oldSchema.fields()) {
      String fieldName = field.name();
      if(TIME_FIELDS.contains(fieldName) && field.schema().type() == Schema.Type.FLOAT64)
        schemaBuilder.field(fieldName, Timestamp.SCHEMA);
      else
        schemaBuilder.field(fieldName, field.schema());
    }
    return schemaBuilder;
  }

  private R applySchemaless(R r) {
    Map<String, Object> value = requireMap(r.value(), PURPOSE);
    Map<String, Object> key = requireMap(r.key(), PURPOSE);
    Map<String, Object> newData = new HashMap<>();
    for (Map.Entry<String, Object> entry : key.entrySet()) {
      newData.put(entry.getKey(), entry.getValue());
    }
    for (Map.Entry<String, Object> entry : value.entrySet()) {
      String fieldName = entry.getKey();
      Object fieldVal = entry.getValue();
      if(TIME_FIELDS.contains(fieldName))
        fieldVal = convertTimestamp(fieldVal);
      newData.put(fieldName, fieldVal);
    }
    newData.put(TIMESTAMP_FIELD, r.timestamp());
    
    return r.newRecord(r.topic(), r.kafkaPartition(), null, r.key(), null, newData, r.timestamp());
  }

  private Object convertTimestamp(Object time){
    if (time instanceof Double) {
      Date result = new Date((long) Math.round((Double) time * 1000));
      return result;
    } else {
      return time; // If it’s not a double, the conversion may not be correct. Skip conversion.
    }
  }

  @Override
  public ConfigDef config() {
    return new ConfigDef();
  }

  @Override
  public void close() {

  }

  @Override
  public void configure(Map<String, ?> map) {

  }
}
