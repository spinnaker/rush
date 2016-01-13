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

package com.netflix.spinnaker.rush.scripts.model

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.annotations.ApiModelProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
class ScriptExecution {

  @ApiModelProperty(value="The id of the execution")
  UUID id

  @ApiModelProperty(value="The status of the execution")
  ScriptExecutionStatus status

  @ApiModelProperty(value="The executed command")
  String command

  @ApiModelProperty(value="The docker image used, if docker is enabled")
  String image

  @ApiModelProperty(value="The docker credentials used, if docker is enabled")
  String credentials

  @ApiModelProperty(value="The id of the launched docker container, if docker is enabled")
  String container

  @ApiModelProperty(value="Any error message")
  String error

  @ApiModelProperty(value="The exit value/code of the execution")
  String statusCode

  @ApiModelProperty(value="The stdout & stderr of the execution")
  String logsContent

  @ApiModelProperty(value="The date/time when the execution was launched")
  Date created

  @ApiModelProperty(value="The last date/time that the execution status was updated")
  Date lastUpdate

}
