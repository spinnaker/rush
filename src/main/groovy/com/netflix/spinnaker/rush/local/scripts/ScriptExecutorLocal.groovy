/*
 * Copyright 2015 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.rush.local.scripts

import com.netflix.spinnaker.rush.scripts.ScriptExecutionRepo
import com.netflix.spinnaker.rush.scripts.ScriptExecutor
import com.netflix.spinnaker.rush.scripts.model.ScriptConfig
import com.netflix.spinnaker.rush.scripts.model.ScriptExecution
import com.netflix.spinnaker.rush.scripts.model.ScriptExecutionStatus
import groovy.util.logging.Slf4j
import org.apache.commons.exec.CommandLine
import org.apache.commons.exec.DefaultExecuteResultHandler
import org.apache.commons.exec.DefaultExecutor
import org.apache.commons.exec.ExecuteWatchdog
import org.apache.commons.exec.Executor
import org.apache.commons.exec.PumpStreamHandler
import org.apache.commons.exec.Watchdog
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import rx.Scheduler
import rx.functions.Action0
import rx.schedulers.Schedulers

import java.util.concurrent.ConcurrentHashMap

@Slf4j
class ScriptExecutorLocal implements ScriptExecutor {

  @Value('${rush.jobs.local.timeoutMinutes:10}')
  long timeoutMinutes

  @Autowired
  ScriptExecutionRepo executionRepo

  Scheduler scheduler = Schedulers.computation()
  Map<String, Map> executionIdToHandlerMap = new ConcurrentHashMap<String, Map>()

  @Override
  String startScript(ScriptConfig configuration) {
    log.info("Starting execution of: command=$configuration.command tokenizedCommand=$configuration.tokenizedCommand...")

    if (configuration.tokenizedCommand) {
      // Build up one command to persist.
      configuration.command = configuration.tokenizedCommand.join(" ")
    }

    String executionId = executionRepo.create(configuration).toString()

    scheduler.createWorker().schedule(
      new Action0() {
        @Override
        public void call() {
          ByteArrayOutputStream stdOutAndErr = new ByteArrayOutputStream()
          PumpStreamHandler pumpStreamHandler = new PumpStreamHandler(stdOutAndErr)
          CommandLine commandLine

          if (configuration.tokenizedCommand) {
            log.info("Executing $executionId with tokenized command: $configuration.tokenizedCommand")

            // Grab the first element as the command.
            commandLine = new CommandLine(configuration.tokenizedCommand[0])

            // Treat the rest as arguments.
            String[] arguments = Arrays.copyOfRange(configuration.tokenizedCommand.toArray(), 1, configuration.tokenizedCommand.size())

            commandLine.addArguments(arguments, false)
          } else if (configuration.command) {
            log.info("Executing $executionId with command: $configuration.command")

            commandLine = CommandLine.parse(configuration.command)
          } else {
            log.info("No command or tokenizedCommand specified for $executionId.")

            throw new IllegalArgumentException("No command or tokenizedCommand specified for $executionId.")
          }

          DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler()
          ExecuteWatchdog watchdog = new ExecuteWatchdog(timeoutMinutes * 60 * 1000){
            @Override
            void timeoutOccured(Watchdog w) {
              // If a watchdog is passed in, this was an actual time-out. Otherwise, it is likely
              // the result of calling watchdog.destroyProcess().
              if (w) {
                log.info("Execution $executionId timed-out (after $timeoutMinutes minutes).")

                cancelExecution(executionId)
              }

              super.timeoutOccured(w)
            }
          }
          Executor executor = new DefaultExecutor()
          executor.setStreamHandler(pumpStreamHandler)
          executor.setWatchdog(watchdog)
          executor.execute(commandLine, resultHandler)

          // Give the execution some time to spin up.
          sleep(500)

          executionRepo.updateStatus(executionId, ScriptExecutionStatus.RUNNING)

          executionIdToHandlerMap.put(executionId, [
            handler: resultHandler,
            watchdog: watchdog,
            stdOutAndErr: stdOutAndErr
          ])

          // Update the status right away so we can fail fast if necessary.
          updateExecution(executionId)
        }
      }
    )

    executionId
  }

  @Override
  ScriptExecutionStatus updateExecution(ScriptExecution execution) {
    try {
      log.info("Polling state for $execution.id...")

      return updateExecution(execution.id.toString())
    } catch (Exception e) {
      log.error("Failed to update $execution.id", e)

      return null
    }
  }

  @Override
  String getLogs(ScriptExecution scriptExecution, ScriptConfig configuration) {
    if (!scriptExecution?.id) {
      return null
    }

    byte[] stdOutAndErr = executionIdToHandlerMap[scriptExecution.id.toString()]?.stdOutAndErr?.toByteArray()

    return stdOutAndErr ? new String(stdOutAndErr) : null
  }

  ScriptExecutionStatus updateExecution(String executionId) {
    if (executionIdToHandlerMap[executionId]) {
      DefaultExecuteResultHandler resultHandler
      ByteArrayOutputStream stdOutAndErr

      executionIdToHandlerMap[executionId].with {
        resultHandler = it.handler
        stdOutAndErr = it.stdOutAndErr
      }

      String logsContent = new String(stdOutAndErr.toByteArray())
      ScriptExecutionStatus executionStatus

      if (resultHandler.hasResult()) {
        log.info("State for $executionId changed with exit code $resultHandler.exitValue.")

        executionRepo.updateField(executionId, 'status_code', resultHandler.exitValue as String)

        if (!logsContent) {
          logsContent = resultHandler.exception ? resultHandler.exception.message : "No output from command."
        }

        executionStatus = resultHandler.exitValue == 0 ? ScriptExecutionStatus.SUCCESSFUL : ScriptExecutionStatus.FAILED

        executionIdToHandlerMap.remove(executionId)
        executionRepo.updateStatus(executionId, executionStatus)
      } else {
        executionStatus = ScriptExecutionStatus.RUNNING
      }

      if (logsContent) {
        // Store base64 encoded logs content.
        executionRepo.updateLogsContent(executionId, logsContent)
      }

      return executionStatus
    } else {
      // This instance of rush is not managing the execution.
      return null
    }
  }

  @Override
  void cancelExecution(String executionId) {
    log.info("Canceling execution $executionId...")

    // This allows the most up-to-date logs to be captured.
    updateExecution(executionId)

    // Terminate the process.
    if (executionIdToHandlerMap[executionId]) {
      executionIdToHandlerMap[executionId].watchdog.destroyProcess()
    }

    // Remove the execution from this rush instance's handler map.
    executionIdToHandlerMap.remove(executionId)

    // Update the status in cassandra.
    executionRepo.updateStatus(executionId, ScriptExecutionStatus.CANCELED)
  }

  @Override
  void synchronizeCanceledExecutions(List<ScriptExecution> runningExecutions) {
    Set<String> runningExecutionIds = runningExecutions.collect { it.id.toString() } as Set
    Set<String> canceledProcesses = executionIdToHandlerMap.keySet() - runningExecutionIds

    canceledProcesses.each { executionId ->
      log.info("Execution $executionId must have been cancelled by something other than this rush instance.")

      cancelExecution(executionId)
    }
  }
}
