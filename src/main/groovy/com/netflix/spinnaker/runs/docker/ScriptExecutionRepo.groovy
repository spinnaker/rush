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

package com.netflix.spinnaker.runs.docker

import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.connectionpool.exceptions.BadRequestException
import com.netflix.astyanax.model.ColumnFamily
import com.netflix.astyanax.serializers.IntegerSerializer
import com.netflix.astyanax.serializers.StringSerializer
import com.netflix.astyanax.util.TimeUUIDUtils
import com.netflix.spinnaker.runs.docker.model.ScriptConfig
import com.netflix.spinnaker.runs.docker.model.ScriptExecutionStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component

@Component
class ScriptExecutionRepo implements ApplicationListener<ContextRefreshedEvent> {

  @Autowired
  Keyspace keyspace

  static ColumnFamily<Integer, String> CF_EXECUTIONS
  static final String CF_NAME = 'application'

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
                  statusCode varchar,
                  PRIMARY KEY (id)
                ) with compression={};'''
    }
  }

  String create(ScriptConfig config) {
    String executionId = TimeUUIDUtils.getUniqueTimeUUIDinMicros().toString()
    runQuery """insert into execution(id,status,command,image) values($id, '${
      ScriptExecutionStatus.PREPARING
    }', '${config.command}', '${config.image}');"""
    executionId
  }

  void updateField(id, field, value) {
    runQuery "update execution set ${field} = '${value}' where id = ${id};"
  }

  private runQuery(String query) {
    keyspace.prepareQuery(CF_EXECUTIONS).withCql(query).execute()
  }

}
