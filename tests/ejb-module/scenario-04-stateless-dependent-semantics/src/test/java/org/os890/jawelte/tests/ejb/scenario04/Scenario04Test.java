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
package org.os890.jawelte.tests.ejb.scenario04;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * TICKET-007 scenario 4 — two injection points for the same
 * {@code @Stateless} type resolve to different underlying instances.
 * The default mapping is {@code @Dependent}; {@code @Dependent} beans
 * are not proxied through a context — each injection point materialises
 * its own instance.
 */
@EnableTestBeans
class Scenario04Test {

    @Inject
    StatelessTagger first;

    @Inject
    StatelessTagger second;

    @Test
    void twoStatelessInjectionPointsResolveToDifferentInstances() {
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.self()).isNotSameAs(second.self());
    }
}
