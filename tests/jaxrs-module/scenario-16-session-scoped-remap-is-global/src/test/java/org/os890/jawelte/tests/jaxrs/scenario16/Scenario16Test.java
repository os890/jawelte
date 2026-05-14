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
package org.os890.jawelte.tests.jaxrs.scenario16;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Scenario 16 — proves the {@code @SessionScoped} remap fires
 * globally even when no resource is registered. The test class
 * carries {@code @EnableTestBeans} but NOT {@code @EnableJaxRs};
 * jaxrs-module's CDI Extension still gets loaded (it is registered
 * through {@code META-INF/services/jakarta.enterprise.inject.spi.Extension})
 * and rewrites the counter bean's {@code @SessionScoped} to
 * {@code @TestMethodScoped}. Per-method reset is then driven by
 * scope-module's adapter.
 *
 * <p>Method 1 increments twice (1, 2 — same instance); method 2
 * increments once (1 — fresh allocation).
 */
@EnableTestBeans
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class Scenario16Test {

    @Inject
    private Scenario16Counter counter;

    @Test
    @Order(1)
    void firstMethodAccumulates() {
        assertThat(counter.increment())
                .as("first call in method 1 sees count 1")
                .isEqualTo(1);
        assertThat(counter.increment())
                .as("second call in method 1 sees count 2 (same @TestMethodScoped instance)")
                .isEqualTo(2);
    }

    @Test
    @Order(2)
    void secondMethodFreshCounter() {
        assertThat(counter.increment())
                .as("first call in method 2 sees count 1 (fresh @TestMethodScoped allocation)")
                .isEqualTo(1);
    }
}
