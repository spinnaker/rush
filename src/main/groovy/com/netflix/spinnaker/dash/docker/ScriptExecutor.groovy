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

package com.netflix.spinnaker.dash.docker

import com.netflix.spinnaker.dash.docker.client.DockerRemoteApiClient
import com.netflix.spinnaker.dash.docker.client.model.ContainerDetails
import com.netflix.spinnaker.dash.docker.client.model.ContainerInfo
import com.netflix.spinnaker.dash.docker.client.model.ContainerStatus
import com.netflix.spinnaker.dash.docker.model.ScriptConfig
import com.netflix.spinnaker.dash.docker.model.ScriptExecutionStatus
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Response

@Slf4j
@Component
class ScriptExecutor {

  @Autowired
  DockerRemoteApiClient dockerClient

  @Autowired
  ScriptExecutionRepo executionRepo

  String startScript(ScriptConfig config) {
    String executionId = executionRepo.create(config)
    String imageName = config.image
    if (!imageName.contains(':')) {
      imageName = "${imageName}:latest"
    }
    executionRepo.updateStatus(executionId, ScriptExecutionStatus.FETCHING_IMAGE)
    boolean imageFound = dockerClient.listImages().collect {
      it.repoTags
    }.flatten().contains(imageName)
    if (!imageFound) {
      Response response = dockerClient.createImage(imageName)
      String responseText = response.body.in().text
      if (responseText.contains("errorDetail")) {
        executionRepo.updateStatus(executionId, ScriptExecutionStatus.FAILED)
        executionRepo.updateField(executionId, 'error', 'cannot retrieve image')
        return
      }
    }
    executionRepo.updateStatus(executionId, ScriptExecutionStatus.RUNNING)
    ContainerInfo containerInfo = dockerClient.createContainer(
      new ContainerDetails(
        image: config.image,
        commands: config.command.split(' ')
      )
    )
    String containerId = containerInfo.id
    executionRepo.updateField(executionId, 'container', containerId)
    try {
      dockerClient.startContainer(containerId)
    } catch (RetrofitError e) {
      executionRepo.updateStatus(executionId, ScriptExecutionStatus.FAILED)
      return
    }
    ContainerStatus status = dockerClient.waitContainer(containerId)
    executionRepo.updateField(executionId, 'status_code', status.statusCode.toString())
    String logs = dockerClient.logsFromContainer(containerId).body.in().text
    executionRepo.updateField(executionId, 'logs', logs)
    executionRepo.updateStatus(executionId,
      status.statusCode == 0 ? ScriptExecutionStatus.SUCCESSFUL : ScriptExecutionStatus.FAILED)
    executionId
  }

}
