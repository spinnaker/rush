/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.dash.scripts

import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.connectionpool.exceptions.BadRequestException
import com.netflix.astyanax.model.ColumnFamily
import com.netflix.astyanax.serializers.IntegerSerializer
import com.netflix.astyanax.serializers.StringSerializer
import com.netflix.astyanax.util.TimeUUIDUtils
import com.netflix.spinnaker.dash.scripts.model.ScriptConfig
import com.netflix.spinnaker.dash.scripts.model.ScriptExecution
import com.netflix.spinnaker.dash.scripts.model.ScriptExecutionStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component

@Component
class ScriptExecutionRepo implements ApplicationListener<ContextRefreshedEvent> {

  @Autowired
  Keyspace keyspace

  static ColumnFamily<Integer, String> CF_EXECUTIONS
  static final String CF_NAME = 'cfexec'

  @Override
  void onApplicationEvent(ContextRefreshedEvent event) {
    CF_EXECUTIONS = ColumnFamily.newColumnFamily(CF_NAME, IntegerSerializer.get(), StringSerializer.get())

    try {
      runQuery '''select * from execution;'''
    } catch (BadRequestException ignored) {
      runQuery '''\
                CREATE TABLE execution(
                  id timeuuid,
                  status varchar,
                  command varchar,
                  image varchar,
                  container varchar,
                  logs text,
                  error text,
                  status_code varchar,
                  PRIMARY KEY (id),
                  created timestamp,
                  last_update timestamp,
                ) with compression={};'''
    }
  }

  String create(ScriptConfig config) {
    UUID executionId = TimeUUIDUtils.getUniqueTimeUUIDinMicros()
    runQuery """insert into execution(id,status,command,image,created,last_update) values($executionId, '${
      ScriptExecutionStatus.PREPARING
    }', '${config.command}', '${config.image}', dateof(now()), dateof(now()));"""
    executionId as String
  }

  void updateField(String id, String field, String value) {
    runQuery "update execution set ${field} = '${value}' where id = ${id};"
  }

  void updateStatus(String id, ScriptExecutionStatus status) {
    updateField(id, 'status', status.toString())
    runQuery "update execution set last_update = dateof(now()) where id = ${id};"
  }

  List<ScriptExecution> list() {
    def result = runQuery("select * from execution;")
    result.result.rows.collect { row ->
      convertRow(row)
    }
  }

  ScriptExecution get(String id) {
    def result = runQuery("select * from execution where id = $id;")
    convertRow(result.result.rows.first())
  }

  private runQuery(String query) {
    keyspace.prepareQuery(CF_EXECUTIONS).withCql(query).execute()
  }

  private ScriptExecution convertRow(def row) {
    new ScriptExecution(
      id: row.columns.getColumnByName('id').getUUIDValue(),
      status: row.columns.getStringValue('status', null),
      command: row.columns.getStringValue('command', null),
      image: row.columns.getStringValue('image', null),
      container: row.columns.getStringValue('container', null),
      logs: row.columns.getStringValue('logs', null),
      error: row.columns.getStringValue('error', null),
      statusCode: row.columns.getStringValue('status_code', null),
      lastUpdate: row.getColumns().getDateValue('last_update', null),
      created: row.getColumns().getDateValue('created', null)
    )
  }

}
