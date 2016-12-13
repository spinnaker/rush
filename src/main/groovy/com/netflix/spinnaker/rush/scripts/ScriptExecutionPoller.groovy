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
import com.netflix.spinnaker.rush.scripts.model.ScriptExecution
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationListener
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.stereotype.Component
import rx.functions.Action0
import rx.schedulers.Schedulers

import java.util.concurrent.TimeUnit

@Slf4j
@Component
class ScriptExecutionPoller implements ApplicationListener<ContextRefreshedEvent> {

  @Value('${rush.polling.orphanedExecutionTimeoutMinutes:30}')
  long orphanedExecutionTimeoutMinutes

  @Value('${rush.polling.runningExecutionUpdateIntervalSeconds:15}')
  long runningExecutionUpdateIntervalSeconds

  @Value('${rush.polling.canceledExecutionUpdateIntervalSeconds:30}')
  long canceledExecutionUpdateIntervalSeconds

  @Autowired
  ScriptExecutor executor

  @Autowired
  ScriptExecutionRepo executionRepo

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository

  void onApplicationEvent(ContextRefreshedEvent event) {
    // Update status of all running executions.
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
          log.error e.message
        }
      } as Action0, 0, runningExecutionUpdateIntervalSeconds, TimeUnit.SECONDS
    )

    // Check for executions managed by this rush instance that are listed in cassandra as cancelled.
    Schedulers.io().createWorker().schedulePeriodically(
      {
        try {
          executor.synchronizeCanceledExecutions(executionRepo.runningExecutions)
        } catch (Exception e) {
          e.printStackTrace()
          log.error e.message
        }
      } as Action0, 0, canceledExecutionUpdateIntervalSeconds, TimeUnit.SECONDS
    )
  }

  private void updateExecution(ScriptExecution execution) {
    if (!executor.updateExecution(execution)) {
      Date currentCassandraTime = executionRepo.currentCassandraTime
      long executionAgeMs = currentCassandraTime.time - execution.created.time
      long executionAgeMinutes = executionAgeMs / 1000 / 60

      log.info("Execution $execution.id ($executionAgeMinutes minutes old) is not managed by this rush instance.")

      if (executionAgeMinutes > orphanedExecutionTimeoutMinutes) {
        log.info("The age of execution $execution.id ($executionAgeMinutes minutes) has exceeded the value of " +
                 "orphanedExecutionTimeoutMinutes ($orphanedExecutionTimeoutMinutes minutes).")

        executor.cancelExecution(execution.id.toString())
      }
    }
  }

}
