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
package org.os890.jawelte.tests.jta.scenario27;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Port of jpa-module scenario 12 — rollback on {@link Error}. The
 * project's {@code TransactionalInterceptor} rolls back on any
 * throwable (RuntimeException, checked Exception, or Error). Under
 * JTA the rollback is driven via {@code JtaTransactionStrategy.rollback()}
 * → {@code TM.rollback()} → XA undo on the enlisted connection.
 */
@EnableTestBeans
public class Scenario27Test {

    @Inject
    private MarkerService markerService;

    /** No-arg constructor for CDI. */
    public Scenario27Test() {
    }

    @Test
    public void errorRollsBackJtaTransaction() {
        assertThatThrownBy(markerService::persistAndError)
                .isInstanceOf(Error.class);

        long count = markerService.countMarkers();
        assertThat(count)
                .as("rolled-back JTA tx must leave zero rows committed even on Error path")
                .isZero();
    }
}
