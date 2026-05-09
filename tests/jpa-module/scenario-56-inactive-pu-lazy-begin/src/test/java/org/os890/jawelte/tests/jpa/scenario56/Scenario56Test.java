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
package org.os890.jawelte.tests.jpa.scenario56;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Two persistence units are declared, but only the managed PU's
 * {@code EntityManager} is dereferenced inside the
 * {@code @Transactional} body. The non-managed PU stays inactive:
 * the {@code TransactionStarted} stream contains exactly one
 * entry, for the managed PU.
 */
@EnableTestBeans
public class Scenario56Test {

    @Inject
    private SinglePuService singlePuService;

    @Inject
    private TxStartObserver txStartObserver;

    /** No-arg constructor for CDI. */
    public Scenario56Test() {
    }

    /** Configured-but-unused PU never has its tx lazy-begun. */
    @Test
    public void inactivePuStaysUnbegun() {
        singlePuService.touchOnlyManagedPu();

        assertThat(txStartObserver.startedPersistenceUnits())
                .as("only the managed PU should fire TransactionStarted; the unused PU stays inactive")
                .containsExactly("testPU56a");
    }
}
