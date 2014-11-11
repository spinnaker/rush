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

import com.netflix.spinnaker.runs.docker.client.DockerRemoteApiClient
import com.netflix.spinnaker.runs.docker.client.model.ContainerDetails
import com.netflix.spinnaker.runs.docker.client.model.ContainerInfo
import com.netflix.spinnaker.runs.docker.client.model.ContainerStatus
import com.netflix.spinnaker.runs.docker.model.ScriptConfig
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.client.Response

@Slf4j
@Component
class ScriptExecutor {

  @Autowired
  DockerRemoteApiClient dockerClient

  @Autowired
  ScriptExecutionRepo executionRepo

  String startScript(ScriptConfig config) {
    if (isImageValid(config.image)) {

      ContainerInfo containerInfo = dockerClient.createContainer(
        new ContainerDetails(
          image: config.image,
          commands: [
            config.command
          ]
        )
      )
      String containerId = containerInfo.id
      dockerClient.startContainer(containerId)
      ContainerStatus status = dockerClient.waitContainer(containerId)
      dockerClient.logsFromContainer(containerId).body.in().text
      return containerId
    } else {
      throw new Exception("Invalid image specified")
    }
  }

  boolean isImageValid(String imageName) {
    if (!imageName.contains(':')) {
      imageName = "${imageName}:latest"
    }
    boolean imageFound = dockerClient.listImages().collect {
      it.repoTags
    }.flatten().contains(imageName)
    if (!imageFound) {
      Response response = dockerClient.createImage(imageName)
      String responseText = response.body.in().text
      if (responseText.contains("errorDetail")) {
        imageFound = false
      } else {
        imageFound = true
      }
    }
    imageFound
  }
}
