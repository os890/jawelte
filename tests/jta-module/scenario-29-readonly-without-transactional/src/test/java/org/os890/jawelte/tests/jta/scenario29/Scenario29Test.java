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
package org.os890.jawelte.tests.jta.scenario29;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Port of jpa-module scenario 17 — {@code @ReadOnly} without
 * {@code @Transactional} is a documented no-op even under JTA: the
 * body runs and its return value reaches the caller unchanged. Same
 * §8.1 caveat as the jpa-module scenario applies — the assertion only
 * proves the body's value reaches the caller, not that the
 * {@code ReadOnlyInterceptor} specifically fired.
 */
@EnableTestBeans
public class Scenario29Test {

    @Inject
    private ReadOnlyOnlyService readOnlyOnlyService;

    /** No-arg constructor for CDI. */
    public Scenario29Test() {
    }

    @Test
    public void readOnlyWithoutTransactionalReturnsBodyValueUnchanged() {
        assertThat(readOnlyOnlyService.computeWithoutTx("hello"))
                .as("the body's return value must reach the caller — "
                        + "@ReadOnly without @Transactional is a no-op pass-through")
                .isEqualTo("readonly:hello");
    }
}
