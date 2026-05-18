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
package org.os890.jawelte.tests.scope.scenario01;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestMethodScoped;

import io.quarkus.test.junit.QuarkusTest;

@EnableTestBeans
@QuarkusTest
class Scenario01Test {

    @Inject
    Counter counter;

    @Test
    void firstMethodIncrementsToOne() {
        assertThat(counter.incrementAndGet()).isEqualTo(1);
        assertThat(counter.incrementAndGet()).isEqualTo(2);
    }

    @Test
    void secondMethodSeesFreshInstance() {
        // If the @TestMethodScoped instance leaked across methods, the
        // counter would already be 2 (carried from the first method) and
        // this would observe 3. A fresh instance starts at 0, so the
        // first incrementAndGet returns 1.
        assertThat(counter.incrementAndGet()).isEqualTo(1);
    }

    @TestMethodScoped
    public static class Counter {

        private int value;

        public int incrementAndGet() {
            return ++value;
        }
    }
}
