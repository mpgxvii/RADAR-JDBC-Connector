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
import io.confluent.connect.jdbc.util.*;
import org.apache.kafka.common.config.AbstractConfig;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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

  static final int CHUNK_TIME_INTERVAL = 86400000;
  static final String DELIMITER = ";";
  static final String HYPERTABLE_WARNING = "A result was returned when none was expected";

  /**
   * Create a new dialect instance with the given connector configuration.
   *
   * @param config the connector configuration; may not be null
   */
  public TimescaleDBDatabaseDialect(AbstractConfig config) {
    super(config);
  }

  @Override
  public List<String> buildCreateTableStatements(
          TableId table,
          Collection<SinkRecordField> fields
  ) {
    // This would create the schema and table then convert the table to a hyper table.
    List<String> sqlQueries = new ArrayList<>();
    if(table.schemaName() != null)
      sqlQueries.add(buildCreateSchemaStatement(table));
    sqlQueries.add(super.buildCreateTableStatement(table, fields));
    sqlQueries.add(buildCreateHyperTableStatement(table));

    return sqlQueries;
  }


  public String buildCreateHyperTableStatement(
          TableId table
  ) {
    ExpressionBuilder builder = expressionBuilder();

    builder.append("SELECT create_hypertable('");
    builder.append(table);
    builder.append("', 'time', migrate_data => TRUE, chunk_time_interval => ");
    builder.append(CHUNK_TIME_INTERVAL);
    builder.append(");");
    return builder.toString();
  }

  public String buildCreateSchemaStatement(
          TableId table
  ) {
    ExpressionBuilder builder = expressionBuilder();

    builder.append("CREATE SCHEMA IF NOT EXISTS ");
    builder.append(table.schemaName());
    return builder.toString();
  }

  @Override
  public void applyDdlStatements(
          Connection connection,
          List<String> statements
  ) throws SQLException {
    // This overrides the function by catching 'result was returned' error thrown by PSQL when creating hypertables
    try{
      super.applyDdlStatements(connection, statements);
    }
    catch(SQLException e){
      if(!e.getMessage().contains(HYPERTABLE_WARNING))
        throw e;
    }
  }


}
