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
package org.os890.jawelte.tests.scope.scenario03;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestMethodScoped;

@EnableTestBeans
class Scenario03Test {

    @Inject
    Bean bean;

    @Test
    void firstMethodEventuallyFiresPreDestroy() {
        bean.touch();
        // PreDestroy fires in afterEach, after this assertion line
        assertThat(Bean.PRE_DESTROY_INVOCATIONS.get()).isLessThan(2);
    }

    @Test
    void secondMethodEventuallyFiresPreDestroy() {
        bean.touch();
    }

    @AfterAll
    static void assertPreDestroyFiredOncePerMethod() {
        // Two test methods; ScopeLifecycleAdapter.afterEach calls
        // deactivate() which destroys all entries via Contextual.destroy,
        // running @PreDestroy. Once per method = 2 invocations.
        assertThat(Bean.PRE_DESTROY_INVOCATIONS.get()).isEqualTo(2);
    }

    @TestMethodScoped
    public static class Bean {

        static final AtomicInteger PRE_DESTROY_INVOCATIONS = new AtomicInteger();

        @PreDestroy
        void onPreDestroy() {
            PRE_DESTROY_INVOCATIONS.incrementAndGet();
        }

        public void touch() {
        }
    }
}
