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

import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.rush.docker.client.DockerInfoUtils
import com.netflix.spinnaker.rush.docker.client.DockerRemoteApiClient
import com.netflix.spinnaker.rush.docker.client.account.Docker
import com.netflix.spinnaker.rush.docker.client.model.ContainerInfo
import com.netflix.spinnaker.rush.docker.client.model.ContainerState
import com.netflix.spinnaker.rush.scripts.model.ScriptExecution
import com.netflix.spinnaker.rush.scripts.model.ScriptExecutionStatus
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component
import retrofit.client.Response
import rx.functions.Action0
import rx.schedulers.Schedulers

import java.util.concurrent.TimeUnit

@Slf4j
@Component
class ScriptExecutionPoller implements ApplicationListener<ContextRefreshedEvent> {

  @Autowired
  ScriptExecutionRepo executionRepo

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository


  void onApplicationEvent(ContextRefreshedEvent event) {
    Schedulers.io().createWorker().schedulePeriodically(
      {
        try {
          rx.Observable.from(executionRepo.runningExecutions)
            .subscribe(
            { ScriptExecution execution ->
              updateExecution(execution)
            },
            {
            log.error("Error: ${it.message}")
            },
            {} as Action0
          )
        } catch (Exception e) {
          log.error e
        }
      } as Action0, 0, 15, TimeUnit.SECONDS
    )
  }

  private void updateExecution(ScriptExecution execution) {
    try {
      log.info('polling state for ' + execution.id + ' with container ' + execution.container)
      Docker dockerInfo = accountCredentialsRepository.getOne(execution.credentials)?.credentials
      DockerRemoteApiClient dockerClient = DockerInfoUtils.getDockerClient(dockerInfo)
      ContainerInfo info = dockerClient.getContainerInfo(execution.container)
      ContainerState state = info.state

      if (!state.isRunning) {
        log.info('state for ' + execution.id + ' changed')
        executionRepo.updateField(execution.id.toString(), 'status_code', state.exitCode as String)

        // Store base64encoded logs content.
        Response logsResponse = dockerClient.getContainerLogs(execution.container)
        String logsResponseContent = logsResponse.body.in().text
        executionRepo.updateLogsContent(execution.id.toString(), logsResponseContent)

        executionRepo.updateStatus(execution.id.toString(),
          (state.exitCode == 0 ? ScriptExecutionStatus.SUCCESSFUL : ScriptExecutionStatus.FAILED))
      }

    } catch (Exception e) {
      log.error("failed to update ${execution.id}", e)
    }
  }

}
