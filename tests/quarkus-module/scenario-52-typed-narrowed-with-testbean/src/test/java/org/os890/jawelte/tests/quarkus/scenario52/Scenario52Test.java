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
package org.os890.jawelte.tests.quarkus.scenario52;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.TestBean;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@EnableTestBeans
@TestBean(bean = CustomGreeting.class)
class Scenario52Test {

    @Inject
    Greeting greeting;

    @Inject
    StatusService statusService;

    @Test
    void typedNarrowedAlternativeCoexistsWithTestBeanForUnrelatedType() {
        // NarrowedImpl implements both Greeting and StatusService but
        // is @Typed-narrowed to StatusService only. @TestBean targets
        // CustomGreeting (for Greeting). Both injection points must
        // resolve correctly:
        //   - Greeting -> CustomGreeting (the @TestBean alternative)
        //   - StatusService -> NarrowedImpl (its only declared bean type)
        assertThat(greeting).isNotNull();
        assertThat(greeting.greet("hello")).isEqualTo("custom:hello");

        assertThat(statusService).isNotNull();
        assertThat(statusService.status()).isEqualTo("OK");
    }
}
