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

package io.confluent.connect.jdbc.sink;

import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.sink.SinkRecord;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.confluent.connect.jdbc.dialect.DatabaseDialect;
import io.confluent.connect.jdbc.util.CachedConnectionProvider;
import io.confluent.connect.jdbc.util.TableId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcDbWriter {
  private static final Logger log = LoggerFactory.getLogger(JdbcDbWriter.class);

  private final JdbcSinkConfig config;
  private final DatabaseDialect dbDialect;
  private final DbStructure dbStructure;
  final CachedConnectionProvider cachedConnectionProvider;

  JdbcDbWriter(final JdbcSinkConfig config, DatabaseDialect dbDialect, DbStructure dbStructure) {
    this.config = config;
    this.dbDialect = dbDialect;
    this.dbStructure = dbStructure;

    this.cachedConnectionProvider = new CachedConnectionProvider(this.dbDialect) {
      @Override
      protected void onConnect(Connection connection) throws SQLException {
        log.info("JdbcDbWriter Connected");
        connection.setAutoCommit(false);
      }
    };
  }

  void write(final Collection<SinkRecord> records) throws SQLException {
    final Connection connection = cachedConnectionProvider.getConnection();

    final Map<TableId, BufferedRecords> bufferByTable = new HashMap<>();
    for (SinkRecord record : records) {
      final TableId tableId = destinationTable(record);
      BufferedRecords buffer = bufferByTable.get(tableId);
      if (buffer == null) {
        buffer = new BufferedRecords(config, tableId, dbDialect, dbStructure, connection);
        bufferByTable.put(tableId, buffer);
      }
      buffer.add(record);
    }
    for (Map.Entry<TableId, BufferedRecords> entry : bufferByTable.entrySet()) {
      TableId tableId = entry.getKey();
      BufferedRecords buffer = entry.getValue();
      log.debug("Flushing records in JDBC Writer for table ID: {}", tableId);
      buffer.flush();
      buffer.close();
    }
    connection.commit();
  }

  void closeQuietly() {
    cachedConnectionProvider.close();
  }

  TableId destinationTable(SinkRecord record) {
    StringBuilder name = new StringBuilder();
    final String schemaName = destinationSchema(record);
    if (!schemaName.isEmpty()) name.append(schemaName).append(".");
    name.append(config.tableNameFormat.replace("${topic}", record.topic()));

    final String tableName = name.toString();

    if (tableName.isEmpty()) {
      throw new ConnectException(String.format(
          "Destination table name for topic '%s' is empty using the format string '%s'",
          record.topic(),
          config.tableNameFormat
      ));
    }
    return dbDialect.parseTableIdentifier(tableName);
  }

  String destinationSchema(SinkRecord record) {
    String schemaNameFormat = config.schemaNameFormat;
    Struct keyData = ((Struct)record.key());

    StringBuilder schemaName = new StringBuilder();
    Pattern pattern = Pattern.compile("\\$\\{(.*?)\\}");
    Matcher matcher = pattern.matcher(schemaNameFormat);
    int lastStart = 0;
    while (matcher.find()) {
      String subString = schemaNameFormat.substring(lastStart,matcher.start());
      String key = matcher.group(1);
      String replacement = keyData.getString(key);
      schemaName.append(subString).append(replacement);
      lastStart = matcher.end();
    }

    return schemaName.toString().toLowerCase();
  }
}
