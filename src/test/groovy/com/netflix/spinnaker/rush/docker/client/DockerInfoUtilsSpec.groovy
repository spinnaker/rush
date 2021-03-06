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

import com.netflix.spinnaker.rush.docker.client.account.Docker
import spock.lang.Shared
import spock.lang.Specification

class DockerInfoUtilsSpec extends Specification {

  @Shared
  Docker dockerInfo = new Docker(url: 'url', registry: 'http://repo')

  void 'generated correct image name'() {
    expect:
    DockerInfoUtils.getImageName(image, dockerInfo) == expectedName

    where:
    image            || expectedName
    'image'          || 'repo/image:latest'
    'image:bluespar' || 'repo/image:bluespar'
  }

}
