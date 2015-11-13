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

package com.netflix.spinnaker.rush.docker.scripts

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.rush.docker.client.DockerInfoUtils
import com.netflix.spinnaker.rush.docker.client.DockerRemoteApiClient
import com.netflix.spinnaker.rush.docker.client.account.Docker
import com.netflix.spinnaker.rush.docker.client.model.ContainerInfo
import com.netflix.spinnaker.rush.docker.client.model.ContainerLaunchDetails
import com.netflix.spinnaker.rush.docker.client.model.ContainerLaunchResult
import com.netflix.spinnaker.rush.docker.client.model.ContainerStartDetails
import com.netflix.spinnaker.rush.docker.client.model.ContainerState
import com.netflix.spinnaker.rush.scripts.ScriptExecutionRepo
import com.netflix.spinnaker.rush.scripts.ScriptExecutor
import com.netflix.spinnaker.rush.scripts.model.ScriptConfig
import com.netflix.spinnaker.rush.scripts.model.ScriptExecution
import com.netflix.spinnaker.rush.scripts.model.ScriptExecutionStatus
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import retrofit.RetrofitError
import retrofit.client.Response
import rx.Scheduler
import rx.functions.Action0
import rx.schedulers.Schedulers

@Slf4j
class ScriptExecutorDocker implements ScriptExecutor {

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository

  @Autowired
  ScriptExecutionRepo executionRepo

  Scheduler scheduler = Schedulers.computation()

  @Override
  String startScript(ScriptConfig configuration) {
    Docker dockerInfo = accountCredentialsRepository.getOne(configuration.credentials)?.credentials
    if (!dockerInfo) {
      new Exception('Invalid credentials specified')
    }
    if (configuration.tokenizedCommand) {
      // Build up one command to persist.
      configuration.command = configuration.tokenizedCommand.join(" ")
    }
    String id = executionRepo.create(configuration).toString()
    scheduler.createWorker().schedule(
      new Action0() {
        @Override
        public void call() {
          processScript(configuration, id, dockerInfo)
        }
      }
    )
    id
  }

  private void processScript(ScriptConfig config, String executionId, Docker dockerInfo) {
    try {
      DockerRemoteApiClient dockerClient = DockerInfoUtils.getDockerClient(dockerInfo)
      String imageName = DockerInfoUtils.getImageName(config.image, dockerInfo)
      executionRepo.updateStatus(executionId, ScriptExecutionStatus.FETCHING_IMAGE)
      log.info("$executionId : fetching ${imageName}")
      Response response = dockerClient.createImage(imageName)
      String responseText = response.body.in().text
      if (responseText.contains("errorDetail")) {
        executionRepo.updateStatus(executionId, ScriptExecutionStatus.FAILED)
        executionRepo.updateField(executionId, 'error', 'cannot retrieve image ' + imageName)
        log.error("$executionId : image ${imageName} could not be fetched")
        return
      }
      log.info("$executionId : image ${imageName} fetched")
      log.info("$executionId : creating container")
      ContainerLaunchDetails details = new ContainerLaunchDetails(image: imageName)
      if (config.tokenizedCommand) {
        log.info("$executionId : executing with tokenized command ${config.tokenizedCommand}")
        details.command = config.tokenizedCommand
      } else if (config.command) {
        log.info("$executionId : executing with command ${config.command}")
        details.command = config.command.split(' ')
      } else {
        log.info("$executionId : using default command")
      }
      ContainerLaunchResult containerInfo = dockerClient.createContainer details
      String containerId = containerInfo.id
      log.info("$executionId : container created with id : $containerId")
      executionRepo.updateField(executionId, 'container', containerId)
      try {
        ContainerStartDetails startDetails = new ContainerStartDetails(privileged: config.privileged)
        dockerClient.startContainer(containerId, startDetails)
        executionRepo.updateStatus(executionId, ScriptExecutionStatus.RUNNING)
      } catch (RetrofitError e) {
        executionRepo.updateStatus(executionId, ScriptExecutionStatus.FAILED)
        return
      }
    } catch (Exception e) {
      e.printStackTrace()
      log.error("failed to start $executionId:", e)
    }

  }

  @Override
  ScriptExecutionStatus updateExecution(ScriptExecution execution) {
    try {
      log.info("Polling state for $execution.id with container $execution.container...")
      Docker dockerInfo = accountCredentialsRepository.getOne(execution.credentials)?.credentials
      DockerRemoteApiClient dockerClient = DockerInfoUtils.getDockerClient(dockerInfo)
      ContainerInfo info = dockerClient.getContainerInfo(execution.container)
      ContainerState state = info.state
      ScriptExecutionStatus executionStatus

      if (!state.isRunning) {
        log.info("State for $execution.id changed with exit code $state.exitCode.")
        executionRepo.updateField(execution.id.toString(), 'status_code', state.exitCode as String)

        // Store base64 encoded logs content.
        Response logsResponse = dockerClient.getContainerLogs(execution.container)
        String logsResponseContent = logsResponse.body.in().text
        executionRepo.updateLogsContent(execution.id.toString(), logsResponseContent)

        executionStatus = state.exitCode == 0 ? ScriptExecutionStatus.SUCCESSFUL : ScriptExecutionStatus.FAILED

        executionRepo.updateStatus(execution.id.toString(), executionStatus)
      } else {
        executionStatus = ScriptExecutionStatus.RUNNING
      }

      return executionStatus
    } catch (Exception e) {
      log.error("Failed to update $execution.id", e)

      return null
    }
  }

  @Override
  String getLogs(ScriptExecution scriptExecution, ScriptConfig configuration) {
    if (!scriptExecution?.container) {
      return null
    }

    Docker dockerInfo = accountCredentialsRepository.getOne(configuration.credentials)?.credentials

    if (!dockerInfo) {
      throw new Exception("Invalid credentials specified.")
    }

    try {
      String containerId = scriptExecution.container
      DockerRemoteApiClient dockerClient = DockerInfoUtils.getDockerClient(dockerInfo)
      Response logsResponse = dockerClient.getContainerLogs(containerId)

      logsResponse.body.in().text
    } catch (Exception e) {
      log.error("Failed to retrieve logs for $scriptExecution.container:", e)
    }
  }

  @Override
  void cancelExecution(String executionId) {
    throw new UnsupportedOperationException()
  }

  @Override
  void synchronizeCanceledExecutions(List<ScriptExecution> runningExecutions) {
    throw new UnsupportedOperationException()
  }
}