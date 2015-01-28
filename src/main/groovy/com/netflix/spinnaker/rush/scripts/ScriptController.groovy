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

import com.netflix.spinnaker.rush.scripts.model.ScriptConfig
import com.netflix.spinnaker.rush.scripts.model.ScriptExecution
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

import javax.validation.Valid

@RestController
class ScriptController {

  @Autowired
  ScriptExecutor executor

  @Autowired
  ScriptExecutionRepo repo

  @RequestMapping(value = 'ops', method = RequestMethod.POST)
  Map<String, String> runScript(@RequestBody @Valid ScriptConfig config) {
    String taskId = executor.startScript(config)
    ['id': taskId]
  }

  @RequestMapping(value = 'tasks', method = RequestMethod.GET)
  List<ScriptExecution> list() {
    repo.list()
  }

  @RequestMapping(value = 'tasks/{id}', method = RequestMethod.GET)
  ScriptExecution get(@PathVariable(value='id')String id) {
    repo.get(id)
  }

  @RequestMapping(value = 'tasks/{id}/logs', method = RequestMethod.POST)
  Map getLogs(@PathVariable(value='id') String id, @RequestBody @Valid ScriptConfig config) {
    ScriptExecution scriptExecution = repo.get(id)
    String logsContent = null

    // First check if the logs have been persisted.
    if (scriptExecution?.logsContent) {
      logsContent = scriptExecution?.logsContent
    } else if (scriptExecution?.container) {
      // If not, fallback is to query Docker directly.
      logsContent = executor.getLogs(scriptExecution.container, config)
    }

    [logsContent: logsContent]
  }

}
