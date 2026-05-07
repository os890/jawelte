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
package org.os890.jawelte.tests.scope.scenario14;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestClassScoped;

@EnableTestBeans
class Scenario14Test {

    @Inject
    Bean bean;

    @Test
    void firstMethodTouchesBean() {
        bean.touch();
    }

    @Test
    void secondMethodTouchesBean() {
        bean.touch();
    }

    @AfterAll
    static void assertPostConstructFiredExactlyOnce() {
        // Two methods touch the bean, but it lives the whole class -
        // @PostConstruct fires only on first injection in the first
        // method. The second method observes the same already-constructed
        // instance.
        assertThat(Bean.POST_CONSTRUCT_INVOCATIONS.get()).isEqualTo(1);
    }

    @TestClassScoped
    public static class Bean {

        static final AtomicInteger POST_CONSTRUCT_INVOCATIONS = new AtomicInteger();

        @PostConstruct
        void onPostConstruct() {
            POST_CONSTRUCT_INVOCATIONS.incrementAndGet();
        }

        public void touch() {
        }
    }
}
