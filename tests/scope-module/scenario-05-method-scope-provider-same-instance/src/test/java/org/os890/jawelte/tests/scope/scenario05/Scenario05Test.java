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
package org.os890.jawelte.tests.scope.scenario05;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestMethodScoped;

@EnableTestBeans
class Scenario05Test {

    @Inject
    Provider<Counter> provider;

    @Test
    void providerGetTwiceReturnsSameBackingInstance() {
        Counter first = provider.get();
        Counter second = provider.get();
        first.increment();
        second.increment();
        // Provider.get() returns proxies; both delegate to the same
        // backing instance for the active scope, so the increments
        // accumulate on a single counter.
        assertThat(first.value()).isEqualTo(2);
        assertThat(second.value()).isEqualTo(2);
    }

    @TestMethodScoped
    public static class Counter {

        private int value;

        public void increment() {
            this.value++;
        }

        public int value() {
            return this.value;
        }
    }
}
