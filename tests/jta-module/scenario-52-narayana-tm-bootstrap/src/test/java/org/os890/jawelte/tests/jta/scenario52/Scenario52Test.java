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
package org.os890.jawelte.tests.jta.scenario52;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jta.api.port.TransactionManagerProvider;

/**
 * Ticket-006 scenario #52 — Narayana TM bootstrap smoke. The
 * scenario pins NarayanaTransactionManagerProvider via a
 * per-scenario META-INF/services override so it always wins the
 * SPI priority sort against the default
 * AutoSelectTransactionManagerProvider — regardless of which
 * JTA-impl profile the parent build activated. A
 * {@code @Transactional} persist + read round-trip commits
 * cleanly through Narayana's {@code TransactionManager} accessor.
 */
@EnableTestBeans
public class Scenario52Test {

    @Inject
    private NarayanaMarkerService service;

    /** No-arg constructor for CDI. */
    public Scenario52Test() {
    }

    @Test
    public void narayanaIsTheActiveProvider() {
        TransactionManagerProvider provider =
                TestContext.loadService(TransactionManagerProvider.class);
        assertThat(provider.name())
                .as("per-scenario META-INF/services override must pin Narayana as the active provider")
                .contains("Narayana");
    }

    @Test
    public void transactionalCommitUnderNarayana() {
        Long generatedId = service.createMarker();
        assertThat(generatedId)
                .as("Narayana-driven @Transactional persist must assign a generated id")
                .isNotNull();

        long count = service.countMarkers();
        assertThat(count)
                .as("a fresh @Transactional read must see the row Narayana just committed")
                .isEqualTo(1L);
    }
}
