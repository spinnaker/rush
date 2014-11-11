package com.netflix.spinnaker.runs.docker.model

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
class ScriptExecution {
  UUID id
  String status
  String command
  String image
  String container
  String logs
  String error
  String statusCode
}
