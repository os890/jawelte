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
package org.os890.jawelte.tests.jta.scenario41;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * {@code @ReadOnly} interceptor under JTA + multi-PU. Read-only
 * queries return correct results; writes inside a {@code @ReadOnly}
 * method are rolled back. Validates the
 * {@code ReadOnlyInterceptor.setRollbackOnly()} path against the
 * JTA strategy when more than one PU is on the classpath.
 */
@EnableTestBeans
public class Scenario41Test {

    @Inject
    private MultiPuReadOnlyService service;

    /** No-arg constructor for CDI. */
    public Scenario41Test() {
    }

    @Test
    public void readOnlyQueryReturnsCorrectCount() {
        assertThat(service.countInPuA())
                .as("@ReadOnly query against PU 'a' must return zero on a fresh DB")
                .isZero();
    }

    @Test
    public void readOnlyDiscardsWritesUnderJtaMultiPu() {
        service.persistInsideReadOnly();
        assertThat(service.countInPuA())
                .as("a @ReadOnly @Transactional persist must be rolled back at JTA commit")
                .isZero();
    }
}
