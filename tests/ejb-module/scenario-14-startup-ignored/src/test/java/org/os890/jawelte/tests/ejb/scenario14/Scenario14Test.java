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
package org.os890.jawelte.tests.ejb.scenario14;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * TICKET-007 scenario 14 — {@code @Startup} is silently ignored.
 * The {@code @PostConstruct} on the {@code @Singleton @Startup}
 * class is not invoked at container bootstrap; only the
 * {@code @Inject} on the test class triggers the lazy lifecycle.
 */
@EnableTestBeans
class Scenario14Test {

    @Inject
    EagerSingleton bean;

    @Test
    void startupDoesNotTriggerEagerInitialization() {
        // Touch the bean now — @ApplicationScoped is lazy, so
        // @PostConstruct fires here, not at container bootstrap.
        assertThat(bean.tag()).isEqualTo("eager");
        assertThat(EagerSingleton.POST_CONSTRUCT_COUNT.get()).isEqualTo(1);
    }
}
