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
package org.os890.jawelte.tests.jta.scenario50;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jta.api.port.TransactionManagerProvider;

/**
 * Ticket-006 scenario #50 — Atomikos TM bootstrap smoke. Under the
 * {@code jta-atomikos} profile, the auto-select wrapper resolves to
 * {@code AtomikosTransactionManagerProvider} (Geronimo absent,
 * Atomikos preferred over Narayana). A {@code @Transactional}
 * persist + read round-trip commits cleanly through Atomikos's
 * {@code UserTransactionManager}.
 */
@EnableTestBeans
public class Scenario50Test {

    @Inject
    private AtomikosMarkerService service;

    /** No-arg constructor for CDI. */
    public Scenario50Test() {
    }

    @Test
    public void autoSelectPicksAtomikos() {
        TransactionManagerProvider provider =
                TestContext.loadService(TransactionManagerProvider.class);
        assertThat(provider.name())
                .as("auto-select must resolve to Atomikos when only Atomikos + Narayana CDI are on the classpath")
                .contains("Atomikos");
    }

    @Test
    public void transactionalCommitUnderAtomikos() {
        Long generatedId = service.createMarker();
        assertThat(generatedId)
                .as("Atomikos-driven @Transactional persist must assign a generated id")
                .isNotNull();

        long count = service.countMarkers();
        assertThat(count)
                .as("a fresh @Transactional read must see the row Atomikos just committed")
                .isEqualTo(1L);
    }
}
