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
package org.os890.jawelte.tests.ejb.scenario02;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * TICKET-007 scenario 2 — two injection points for the same
 * {@code @Singleton} resolve to the same underlying instance.
 * Increment via one injection point, observe the new value via the
 * other.
 */
@EnableTestBeans
class Scenario02Test {

    @Inject
    Counter first;

    @Inject
    Counter second;

    @Test
    void twoInjectionPointsShareTheSameSingletonInstance() {
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();

        int before = first.value();
        first.increment();

        assertThat(second.value()).isEqualTo(before + 1);
    }
}
