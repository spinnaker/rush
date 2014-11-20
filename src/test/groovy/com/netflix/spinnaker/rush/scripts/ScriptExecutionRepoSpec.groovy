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
import com.netflix.spinnaker.kork.astyanax.AstyanaxComponents
import com.netflix.spinnaker.rush.scripts.model.ScriptConfig
import com.netflix.spinnaker.rush.scripts.model.ScriptExecution
import com.netflix.spinnaker.rush.scripts.model.ScriptExecutionStatus
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class ScriptExecutionRepoSpec extends Specification {

  @Shared
  @AutoCleanup('destroy')
  AstyanaxComponents.EmbeddedCassandraRunner runner

  @Shared
  @Subject
  ScriptExecutionRepo repo

  @Shared
  Keyspace keyspace

  void setupSpec() {
    int port = 9160
    int storagePort = 7000
    String host = '127.0.0.1'

    AstyanaxComponents components = new AstyanaxComponents()
    keyspace = components.keyspaceFactory(
      components.astyanaxConfiguration(),
      components.connectionPoolConfiguration(port, host, 3),
      components.connectionPoolMonitor()
    ).getKeyspace('workflow', 'test')
    runner = new AstyanaxComponents.EmbeddedCassandraRunner(keyspace, port, storagePort, host)
    runner.init()
    repo = new ScriptExecutionRepo()
    repo.keyspace = keyspace
    repo.onApplicationEvent(null)
  }

  void setup() {
    keyspace.prepareQuery(ScriptExecutionRepo.CF_EXECUTIONS).withCql('truncate execution;').execute()
  }

  void 'can create and retrieve new executions'() {
    when:
    String id = repo.create(new ScriptConfig(image: 'image1', command: 'bash'))
    String id2 = repo.create(new ScriptConfig(image: 'image2', command: 'ls'))
    ScriptExecution execution = repo.get(id)

    then:
    id != null
    id != id2
    execution != null
    execution.image == 'image1'
    execution.command == 'bash'
    execution.status == 'PREPARING'
  }

  void 'new ids are added to the list of executions'() {
    expect:
    repo.list().empty

    when:
    String id = repo.create(new ScriptConfig(image: 'image1', command: 'bash'))
    List list = repo.list()

    then:
    list.size() == 1
    list.first().id.toString() == id
  }

  void 'should update time when updating status'() {
    given:
    String id = repo.create(new ScriptConfig(image: 'image1', command: 'bash'))
    ScriptExecution execution = repo.get(id)

    expect:
    execution.lastUpdate == execution.created
    execution.status == 'PREPARING'

    when:
    repo.updateStatus(id, ScriptExecutionStatus.FAILED)
    execution = repo.get(id)

    then:
    execution.lastUpdate > execution.created
    execution.status == 'FAILED'
  }

  void 'should update fields'() {
    given:
    String logs = 'this is the new logs'
    String id = repo.create(new ScriptConfig(image: 'image1', command: 'bash'))
    ScriptExecution execution = repo.get(id)

    expect:
    execution.statusCode == null

    when:
    repo.updateField(id, 'status_code', '0')
    execution = repo.get(id)

    then:
    execution.statusCode == '0'
  }

  void 'can get running jobs'() {
    given:
    String id1 = repo.create(new ScriptConfig(image: 'image1', command: 'bash'))
    repo.updateStatus(id1, ScriptExecutionStatus.FAILED)

    String id2 = repo.create(new ScriptConfig(image: 'image1', command: 'bash'))
    repo.updateStatus(id2, ScriptExecutionStatus.RUNNING)

    String id3 = repo.create(new ScriptConfig(image: 'image1', command: 'bash'))
    repo.updateStatus(id3, ScriptExecutionStatus.RUNNING)

    expect:
    repo.runningExecutions.collect { it.id.toString() } == [id2, id3]
  }

}
