/*
 * Copyright 2015 Netflix, Inc.
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
import com.wordnik.swagger.annotations.ApiOperation
import com.wordnik.swagger.annotations.ApiParam
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

  @ApiOperation(value = "Execute a command", notes = "Execute the specified command via either docker or a local process. Returns a map with an id key identifying the new execution.", response = Map)
  @RequestMapping(value = 'ops', method = RequestMethod.POST)
  Map<String, String> runScript(@ApiParam(value="The command to execute", required=true) @RequestBody @Valid ScriptConfig config) {
    String taskId = executor.startScript(config)
    ['id': taskId]
  }

  @ApiOperation(value = "List all executions", notes = "List all executions in the execution repository backing this engine.")
  @RequestMapping(value = 'tasks', method = RequestMethod.GET)
  List<ScriptExecution> list() {
    repo.list()
  }

  @ApiOperation(value = "Get execution details", notes = "Get all the details of the specified execution, except for the logs.")
  @RequestMapping(value = 'tasks/{id}', method = RequestMethod.GET)
  ScriptExecution get(@ApiParam(value="The id of the execution", required=true) @PathVariable(value='id') String id) {
    repo.get(id, false)
  }

  @ApiOperation(value = "Cancel an execution", notes = "Cancel an execution, without regard to the current status.")
  @RequestMapping(value = 'tasks/{id}/cancel', method = RequestMethod.POST)
  String cancel(@ApiParam(value="The id of the execution", required=true) @PathVariable(value='id') String id) {
    executor.cancelExecution(id)

    return "Canceled execution $id."
  }

  @ApiOperation(value = "Get execution logs", notes = "Get the logs of the specified execution.")
  @RequestMapping(value = 'tasks/{id}/logs', method = RequestMethod.POST)
  Map getLogs(@ApiParam(value="The id of the execution", required=true) @PathVariable(value='id') String id,
              @ApiParam(value="The same configuration originally used to launch the execution. This is only needed if running docker, and is used in case the logs are not persisted and need to be retrieved directly from the executor") @RequestBody(required = false) ScriptConfig config) {
    ScriptExecution scriptExecution = repo.get(id, true)
    String logsContent = null

    // First check if the logs have been persisted.
    if (scriptExecution?.logsContent) {
      logsContent = scriptExecution?.logsContent
    } else {
      // If not, fallback is to query the executor directly.
      logsContent = executor.getLogs(scriptExecution, config)
    }

    [logsContent: logsContent]
  }

}
