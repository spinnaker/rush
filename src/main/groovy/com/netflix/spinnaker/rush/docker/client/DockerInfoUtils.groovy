package com.netflix.spinnaker.rush.docker.client

import static retrofit.Endpoints.newFixedEndpoint

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.rush.docker.client.account.Docker
import com.squareup.okhttp.OkHttpClient
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter

import java.util.concurrent.TimeUnit

class DockerInfoUtils {

  static Map<String, DockerRemoteApiClient> dockerClients = [:]

  static String getImageName(String image, Docker dockerInfo) {
    String imageName = image
    if (!imageName.contains(':')) {
      imageName = "${imageName}:latest"
    }
    String url = dockerInfo.registry.replaceFirst("${new URL(dockerInfo.registry).getProtocol()}://", "");
    "${url}/${imageName}"
  }

  static DockerRemoteApiClient getDockerClient(Docker dockerInfo) {
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

}
