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

package com.netflix.spinnaker.rush.docker.client

import com.netflix.spinnaker.rush.docker.client.model.ContainerLaunchDetails
import com.netflix.spinnaker.rush.docker.client.model.ContainerLaunchResult
import com.netflix.spinnaker.rush.docker.client.model.ContainerInfo
import retrofit.client.Response
import retrofit.http.Body
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.Path
import retrofit.http.Query
import retrofit.http.Streaming

interface DockerRemoteApiClient {

  @POST('/containers/create')
  ContainerLaunchResult createContainer(@Body ContainerLaunchDetails details)

  @POST('/containers/{id}/start')
  Response startContainer(@Path('id') String id)

  @GET('/containers/{id}/json')
  ContainerInfo getContainerInfo(@Path('id') String id)

  @Streaming
  @POST('/images/create')
  Response createImage(@Query('fromImage') String fromImage)

}
