/*
 * Copyright 2026 os890
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
package example.jaxrs;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jaxrs.api.EnableJaxRs;
import org.os890.jawelte.module.jaxrs.api.TestUrl;

@EnableTestBeans
@EnableJaxRs(restResources = {HelloResource.class})
class HelloResourceTest {

    @Inject
    TestUrl testUrl;

    @Test
    void serverBootedOnOsAssignedPort() {
        String baseUrl = testUrl.get();
        URI uri = URI.create(baseUrl);
        assertThat(uri.getScheme()).isEqualTo("http");
        assertThat(uri.getHost()).isEqualTo("localhost");
        assertThat(uri.getPort()).isPositive();
    }
}
