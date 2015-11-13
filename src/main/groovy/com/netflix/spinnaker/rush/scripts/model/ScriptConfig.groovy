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

import com.wordnik.swagger.annotations.ApiModelProperty

class ScriptConfig {

  @ApiModelProperty(value="The command to execute. Either command or tokenizedCommand should be specified. tokenizedCommand will take precedence")
  String command

  @ApiModelProperty(value="The tokenized command to execute. Either command or tokenizedCommand should be specified. tokenizedCommand will take precedence")
  List<String> tokenizedCommand

  @ApiModelProperty(value="The docker image to use when executing the command. Only required if using docker")
  String image

  @ApiModelProperty(value="The credentials identifying the docker host. Only required if using docker")
  String credentials

  @ApiModelProperty(value="Whether to execute the command using --privileged mode. Only required if using docker")
  boolean privileged

}
