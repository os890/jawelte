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
package org.os890.jawelte.tests.jta.scenario54;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jta.api.port.TransactionManagerProvider;

/**
 * Ticket-006 scenario #54 — Geronimo TM bootstrap smoke. The
 * scenario pins GeronimoTransactionManagerProvider via a
 * per-scenario META-INF/services override + brings
 * geronimo-transaction into its classpath, so it always runs
 * against Geronimo regardless of which JTA-impl profile the parent
 * build activated. A {@code @Transactional} persist + read
 * round-trip commits cleanly through Geronimo's
 * {@code GeronimoTransactionManager}.
 */
@EnableTestBeans
public class Scenario54Test {

    @Inject
    private GeronimoMarkerService service;

    /** No-arg constructor for CDI. */
    public Scenario54Test() {
    }

    @Test
    public void geronimoIsTheActiveProvider() {
        TransactionManagerProvider provider =
                TestContext.loadService(TransactionManagerProvider.class);
        assertThat(provider.name())
                .as("per-scenario META-INF/services override must pin Geronimo as the active provider")
                .contains("Geronimo");
    }

    @Test
    public void transactionalCommitUnderGeronimo() {
        Long generatedId = service.createMarker();
        assertThat(generatedId)
                .as("Geronimo-driven @Transactional persist must assign a generated id")
                .isNotNull();

        long count = service.countMarkers();
        assertThat(count)
                .as("a fresh @Transactional read must see the row Geronimo just committed")
                .isEqualTo(1L);
    }
}
