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

package com.netflix.spinnaker.runs.config

import static retrofit.Endpoints.newFixedEndpoint

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.runs.docker.client.DockerRemoteApiClient
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit.RestAdapter
import retrofit.RestAdapter.LogLevel
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

@Configuration
@CompileStatic
class DockerRemoteApiConfig {

  @Bean
  DockerRemoteApiClient dockerClient(@Value('${docker.host}') String dockerHost) {

    ObjectMapper objectMapper = new ObjectMapper()
    objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    new RestAdapter.Builder()
      .setEndpoint(newFixedEndpoint(dockerHost))
      .setClient(new OkClient())
      .setLogLevel(LogLevel.BASIC)
      .setConverter(new JacksonConverter(objectMapper))
      .build()
      .create(DockerRemoteApiClient)
  }

}
