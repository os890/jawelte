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
package org.os890.jawelte.tests.ejb.scenario12;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * TICKET-007 scenario 12 — {@code @TransactionAttribute} on a
 * {@code @Singleton} method is silently ignored by ejb-module. The
 * class-level implicit {@code @Transactional} (TxType.REQUIRED)
 * still drives the method, so the persist commits exactly as it
 * does for scenario 5.
 */
@EnableTestBeans
class Scenario12Test {

    @Inject
    NoteRepository notes;

    @Test
    void transactionAttributeIgnoredButImplicitTransactionalApplies() {
        Long id = notes.save("ta-ignored");
        assertThat(id).isNotNull();
        assertThat(notes.count()).isEqualTo(1L);
    }
}
