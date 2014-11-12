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

import com.netflix.spinnaker.dash.docker.client.DockerRemoteApiClient
import com.netflix.spinnaker.dash.docker.client.model.ContainerDetails
import com.netflix.spinnaker.dash.docker.client.model.ContainerInfo
import com.netflix.spinnaker.dash.docker.client.model.ContainerStatus
import com.netflix.spinnaker.dash.scripts.model.ScriptConfig
import com.netflix.spinnaker.dash.scripts.model.ScriptExecutionStatus
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Response
import rx.Scheduler
import rx.functions.Action0
import rx.schedulers.Schedulers

@Slf4j
@Component
class ScriptExecutor {

  @Autowired
  DockerRemoteApiClient dockerClient

  @Autowired
  ScriptExecutionRepo executionRepo

  Scheduler scheduler = Schedulers.computation()

  String startScript(ScriptConfig configuration) {
    String id = executionRepo.create(configuration).toString()
    scheduler.createWorker().schedule(
      new Action0() {
        @Override
        public void call() {
          processScript(configuration, id)
        }
      }
    )
    id
  }

  private void processScript(ScriptConfig config, String executionId) {
    try {
      String imageName = config.image
      log.info("$executionId : checking for latest image: ${config.image}")
      if (!imageName.contains(':')) {
        imageName = "${imageName}:latest"
      }
      executionRepo.updateStatus(executionId, ScriptExecutionStatus.FETCHING_IMAGE)
      boolean imageFound = dockerClient.listImages().collect {
        it.repoTags
      }.flatten().contains(imageName)
      if (imageFound) {
        log.info("$executionId : image ${config.image} found")
      } else {
        log.info("$executionId : image ${config.image} not in docker host, fetching")
        Response response = dockerClient.createImage(imageName)
        String responseText = response.body.in().text
        if (responseText.contains("errorDetail")) {
          executionRepo.updateStatus(executionId, ScriptExecutionStatus.FAILED)
          executionRepo.updateField(executionId, 'error', 'cannot retrieve image')
          log.error("$executionId : image ${config.image} could not be fetched")
          return
        }
        log.info("$executionId : image ${config.image} fetched")
      }
      executionRepo.updateStatus(executionId, ScriptExecutionStatus.RUNNING)
      log.info("$executionId : creating container")
      ContainerInfo containerInfo = dockerClient.createContainer(
        new ContainerDetails(
          image: config.image,
          commands: config.command.split(' ')
        )
      )
      String containerId = containerInfo.id
      log.info("$executionId : container created with id : $containerId")
      executionRepo.updateField(executionId, 'container', containerId)
      try {
        dockerClient.startContainer(containerId)
      } catch (RetrofitError e) {
        executionRepo.updateStatus(executionId, ScriptExecutionStatus.FAILED)
        return
      }
      log.info("$executionId : waiting for container : $containerId")
      ContainerStatus status = dockerClient.waitContainer(containerId)
      executionRepo.updateField(executionId, 'status_code', status.statusCode.toString())
      log.info("$executionId : waiting finished with status code : ${status.statusCode}")
      String logs = dockerClient.logsFromContainer(containerId).body.in().text
      executionRepo.updateField(executionId, 'logs', logs)
      executionRepo.updateStatus(executionId,
        status.statusCode == 0 ? ScriptExecutionStatus.SUCCESSFUL : ScriptExecutionStatus.FAILED)
      log.info("$executionId : script finished")
    } catch (Exception e) {
      log.error("failed to run $executionId:", e)
    }
  }


}
