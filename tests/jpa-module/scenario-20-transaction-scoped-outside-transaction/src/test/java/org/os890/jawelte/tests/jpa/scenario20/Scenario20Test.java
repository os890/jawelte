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
package org.os890.jawelte.tests.jpa.scenario20;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Dereferencing a {@code @TransactionScoped} bean outside any
 * {@code @Transactional} (or {@code UserTransaction.begin()}) raises
 * {@link ContextNotActiveException}. The test method itself is NOT
 * {@code @Transactional} so the calling thread has no active tx scope.
 */
@EnableTestBeans
public class Scenario20Test {

    @Inject
    private OutsideTxTracker tracker;

    /** No-arg constructor for CDI. */
    public Scenario20Test() {
    }

    /** Touching a @TransactionScoped bean outside any tx → ContextNotActiveException. */
    @Test
    public void dereferenceOutsideTxRaisesContextNotActiveException() {
        assertThatThrownBy(tracker::touch)
                .as("with no @Transactional on the calling thread, the tx scope is inactive "
                        + "and any proxy method must surface ContextNotActiveException")
                .isInstanceOf(ContextNotActiveException.class);
    }
}
