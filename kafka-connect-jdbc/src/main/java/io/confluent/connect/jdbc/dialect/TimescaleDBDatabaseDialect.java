/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.connect.jdbc.dialect;

import io.confluent.connect.jdbc.dialect.DatabaseDialectProvider.SubprotocolBasedProvider;
import io.confluent.connect.jdbc.sink.metadata.SinkRecordField;
import io.confluent.connect.jdbc.util.DateTimeUtils;
import io.confluent.connect.jdbc.util.ExpressionBuilder;
import io.confluent.connect.jdbc.util.TableId;
import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.Timestamp;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link DatabaseDialect} for TimescaleDB.
 */
public class TimescaleDBDatabaseDialect extends PostgreSqlDatabaseDialect {

  /**
   * The provider for {@link TimescaleDBDatabaseDialect}.
   */
  public static class Provider extends SubprotocolBasedProvider {
    public Provider() {
      super(TimescaleDBDatabaseDialect.class.getSimpleName(), "postgresql");
    }

    @Override
    public DatabaseDialect create(AbstractConfig config) {
      return new TimescaleDBDatabaseDialect(config);
    }
  }

  static final String CHUNK_TIME_INTERVAL = "1 day";
  static final String DELIMITER = ";";
  static final String HYPERTABLE_WARNING = "A result was returned when none was expected";
  static final String TIME_COLUMN = "time";

  /**
   * Create a new dialect instance with the given connector configuration.
   *
   * @param config the connector configuration; may not be null
   */
  public TimescaleDBDatabaseDialect(AbstractConfig config) {
    super(config);
  }

  @Override
  public List<String> buildCreateTableStatements(TableId table, Collection<SinkRecordField> fields) {
    // This would create the schema and table then convert the table to a hyper
    // table.
    List<String> sqlQueries = new ArrayList<>();
    if (table.schemaName() != null) {
      sqlQueries.add(buildCreateSchemaStatement(table));
    }
    Optional<SinkRecordField> timeField = getTimeField(fields);

    if (!timeField.isPresent()) {
      log.warn("Time column is not present. Skipping hypertable creation..");
      return Collections.EMPTY_LIST;
    }
       
    sqlQueries.add(super.buildCreateTableStatement(table, fields));
    sqlQueries.add(buildCreateHyperTableStatement(table, timeField.get().name()));

    return sqlQueries;
  }

  public String buildCreateHyperTableStatement(TableId table, String timeColumn) {
    ExpressionBuilder builder = expressionBuilder();

    builder.append("SELECT create_hypertable('");
    builder.append(table);
    builder.append("', '");
    builder.append(timeColumn);
    builder.append("', migrate_data => TRUE, chunk_time_interval => INTERVAL '");
    builder.append(CHUNK_TIME_INTERVAL);
    builder.append("');");
    return builder.toString();
  }

  public String buildCreateSchemaStatement(TableId table) {
    ExpressionBuilder builder = expressionBuilder();

    builder.append("CREATE SCHEMA IF NOT EXISTS ");
    builder.append(table.schemaName());
    return builder.toString();
  }

  private Optional<SinkRecordField> getTimeField(Collection<SinkRecordField> fields) {
    return fields.stream()
                  .filter(p -> p.name().toLowerCase().equals(TIME_COLUMN))
                  .findFirst();
  }

  @Override
  public void applyDdlStatements(Connection connection, List<String> statements) throws SQLException {
    // This overrides the function by catching 'result was returned' error thrown by
    // PSQL
    // when creating hypertables
    try {
      super.applyDdlStatements(connection, statements);
    } catch (SQLException e) {
      if (!e.getMessage().contains(HYPERTABLE_WARNING)) {
        throw e;
      }
    }
  }

  @Override
  protected String getSqlType(SinkRecordField field) {
    if (field.schemaName() == Timestamp.LOGICAL_NAME) {
      return "TIMESTAMPTZ";
    } else {
      return super.getSqlType(field);
    }
  }

  @Override
  protected void formatColumnValue(ExpressionBuilder builder, String schemaName, Map<String, String> schemaParameters,
      Schema.Type type, Object value) {
    if (schemaName == org.apache.kafka.connect.data.Timestamp.LOGICAL_NAME) {
      builder.appendStringQuoted(DateTimeUtils.formatTimestamptz((java.util.Date) value, super.timeZone()));
    } else {
      super.formatColumnValue(builder, schemaName, schemaParameters, type, value);
    }
  }

}
