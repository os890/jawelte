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
package org.os890.jawelte.tests.scope.scenario12;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestClassScoped;

@EnableTestBeans
class Scenario12Test {

    @Inject
    Counter counter;

    @Test
    void firstMethodIncrementsClassScopedCounter() {
        counter.increment();
        // After this increment, the counter has a value visible to
        // every other method in the class - order-independent assert
        // is in @AfterAll.
        assertThat(counter.value()).isPositive();
    }

    @Test
    void secondMethodIncrementsClassScopedCounter() {
        counter.increment();
        assertThat(counter.value()).isPositive();
    }

    @AfterAll
    static void assertClassScopeStateAccumulatedAcrossMethods() {
        // Two methods, each incrementing once. If the @TestClassScoped
        // instance leaked (got recreated between methods), the counter
        // would be 1 here. Since state survives, it is 2.
        assertThat(Counter.TERMINAL_VALUE.get()).isEqualTo(2);
    }

    @TestClassScoped
    public static class Counter {

        static final AtomicInteger TERMINAL_VALUE = new AtomicInteger();

        private int value;

        public void increment() {
            this.value++;
            TERMINAL_VALUE.set(this.value);
        }

        public int value() {
            return this.value;
        }
    }
}
