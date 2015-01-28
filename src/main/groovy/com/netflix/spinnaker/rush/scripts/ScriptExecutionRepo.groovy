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

package com.netflix.spinnaker.rush.scripts

import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.connectionpool.exceptions.BadRequestException
import com.netflix.astyanax.model.ColumnFamily
import com.netflix.astyanax.serializers.IntegerSerializer
import com.netflix.astyanax.serializers.StringSerializer
import com.netflix.astyanax.util.TimeUUIDUtils
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.rush.scripts.model.ScriptConfig
import com.netflix.spinnaker.rush.scripts.model.ScriptExecution
import com.netflix.spinnaker.rush.scripts.model.ScriptExecutionStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component

@Component
class ScriptExecutionRepo implements ApplicationListener<ContextRefreshedEvent> {

  @Autowired
  Keyspace keyspace

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository

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
                  credentials varchar,
                  logs_content varchar,
                  error varchar,
                  status_code varchar,
                  created timestamp,
                  last_update timestamp,
                  PRIMARY KEY (id)
                ) with compression={};'''

      runQuery '''CREATE INDEX execution_status ON execution (status);'''
    }
  }

  String create(ScriptConfig config) {
    UUID executionId = TimeUUIDUtils.getUniqueTimeUUIDinMicros()
    runQuery """insert into execution(id,status,command,image,credentials,created,last_update) values($executionId, '${
      ScriptExecutionStatus.PREPARING
    }', '${config.command}', '${config.image}', '${config.credentials}', dateof(now()), dateof(now()));"""
    executionId as String
  }

  void updateField(String id, String field, String value) {
    runQuery "update execution set ${field} = '${value}' where id = ${id};"
  }

  void updateLogsContent(String id, String logsContent) {
    def base64EncodedLogsContent = logsContent.bytes.encodeBase64().toString()
    updateField(id, 'logs_content', base64EncodedLogsContent)
  }

  void updateStatus(String id, ScriptExecutionStatus status) {
    updateField(id, 'status', status.toString())
    runQuery "update execution set last_update = dateof(now()) where id = ${id};"
  }

  List<ScriptExecution> list() {
    def result = runQuery("select * from execution;")
    result.result.rows.collect { row ->
      convertRow(row, false)
    }
  }

  List<ScriptExecution> getRunningExecutions() {
    def result = runQuery("select * from execution where status = 'RUNNING';")
    result.result.rows.collect { row ->
      convertRow(row, false)
    }
  }

  ScriptExecution get(String id, boolean includeLogsContent) {
    def result = runQuery("select * from execution where id = $id;")
    convertRow(result.result.rows.first(), includeLogsContent)
  }

  private runQuery(String query) {
    keyspace.prepareQuery(CF_EXECUTIONS).withCql(query).execute()
  }

  private static base64Decode(String base64EncodedStr) {
    if (base64EncodedStr) {
      new String(base64EncodedStr.decodeBase64())
    } else {
      null
    }
  }

  private ScriptExecution convertRow(def row, boolean includeLogsContent) {
    def scriptExecution = new ScriptExecution(
      id: row.columns.getColumnByName('id').getUUIDValue(),
      status: row.columns.getStringValue('status', null),
      command: row.columns.getStringValue('command', null),
      image: row.columns.getStringValue('image', null),
      container: row.columns.getStringValue('container', null),
      credentials: row.columns.getStringValue('credentials', null),
      error: row.columns.getStringValue('error', null),
      statusCode: row.columns.getStringValue('status_code', null),
      lastUpdate: row.getColumns().getDateValue('last_update', null),
      created: row.getColumns().getDateValue('created', null)
    )

    if (includeLogsContent) {
      scriptExecution.logsContent = base64Decode(row.getColumns().getStringValue('logs_content', null))
    }

    scriptExecution
  }

}
