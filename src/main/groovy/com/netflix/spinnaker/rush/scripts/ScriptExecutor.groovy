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

import static retrofit.Endpoints.newFixedEndpoint

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.annotations.VisibleForTesting
import com.netflix.spinnaker.amos.AccountCredentialsRepository
import com.netflix.spinnaker.rush.docker.client.DockerRemoteApiClient
import com.netflix.spinnaker.rush.docker.client.account.Docker
import com.netflix.spinnaker.rush.docker.client.model.ContainerDetails
import com.netflix.spinnaker.rush.docker.client.model.ContainerInfo
import com.netflix.spinnaker.rush.docker.client.model.ContainerStatus
import com.netflix.spinnaker.rush.scripts.model.ScriptConfig
import com.netflix.spinnaker.rush.scripts.model.ScriptExecutionStatus
import com.squareup.okhttp.OkHttpClient
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RestAdapter
import retrofit.RetrofitError
import retrofit.client.OkClient
import retrofit.client.Response
import retrofit.converter.JacksonConverter
import rx.Scheduler
import rx.functions.Action0
import rx.schedulers.Schedulers

import java.util.concurrent.TimeUnit

@Slf4j
@Component
class ScriptExecutor {

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository

  @Autowired
  ScriptExecutionRepo executionRepo

  Scheduler scheduler = Schedulers.computation()

  Map<String, DockerRemoteApiClient> dockerClients = [:]

  String startScript(ScriptConfig configuration) {
    Docker dockerInfo = accountCredentialsRepository.getOne(configuration.credentials)?.credentials
    if (!dockerInfo) {
      new Exception('Invalid credentials specified')
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
      DockerRemoteApiClient dockerClient = getDockerClient(dockerInfo)
      String imageName = getImageName(config.image, dockerInfo)
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
      executionRepo.updateStatus(executionId, ScriptExecutionStatus.RUNNING)
      log.info("$executionId : creating container")
      ContainerInfo containerInfo = dockerClient.createContainer(
        new ContainerDetails(
          image: imageName,
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

  @VisibleForTesting
  private DockerRemoteApiClient getDockerClient(Docker dockerInfo) {
    String url = dockerInfo.url
    if (!dockerClients.containsKey(url)) {
      ObjectMapper objectMapper = new ObjectMapper()
      objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

      OkHttpClient okClient = new OkHttpClient()
      okClient.setConnectTimeout(20, TimeUnit.MINUTES)
      okClient.setReadTimeout(20, TimeUnit.MINUTES)

      DockerRemoteApiClient client = new RestAdapter.Builder()
        .setEndpoint(newFixedEndpoint(dockerInfo.url))
        .setClient(new OkClient(okClient))
        .setLogLevel(RestAdapter.LogLevel.BASIC)
        .setConverter(new JacksonConverter(objectMapper))
        .build()
        .create(DockerRemoteApiClient)

      dockerClients.put(url, client)
    }
    dockerClients.get(url)
  }

  @VisibleForTesting
  private String getImageName(String image, Docker dockerInfo) {
    String imageName = image
    if (!imageName.contains(':')) {
      imageName = "${imageName}:latest"
    }
    String url = dockerInfo.registry.replaceFirst("${new URL(dockerInfo.registry).getProtocol()}://", "");
    "${url}/${imageName}"
  }

}
