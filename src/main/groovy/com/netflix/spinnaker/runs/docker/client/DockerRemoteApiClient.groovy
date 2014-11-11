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

package com.netflix.spinnaker.runs.docker.client

import com.netflix.spinnaker.runs.docker.client.model.ContainerDetails
import com.netflix.spinnaker.runs.docker.client.model.ContainerInfo
import com.netflix.spinnaker.runs.docker.client.model.ContainerStatus
import com.netflix.spinnaker.runs.docker.client.model.Image
import retrofit.client.Response
import retrofit.http.Body
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.Path
import retrofit.http.Query
import retrofit.http.Streaming

interface DockerRemoteApiClient {

  @POST('/containers/create')
  ContainerInfo createContainer(@Body ContainerDetails details)

  @POST('/containers/{id}/start')
  Response startContainer(@Path(value = 'id') String id)

  @POST('/containers/{id}/wait')
  ContainerStatus waitContainer(@Path(value = 'id') String id)

  @Streaming
  @GET('/containers/{id}/logs?stderr=1&stdout=1')
  Response logsFromContainer(@Path(value = 'id') String id)

  @GET('/images/json')
  List<Image> listImages()

  @Streaming
  @POST('/images/create')
  Response createImage(@Query(value = 'fromImage') String fromImage)

}
