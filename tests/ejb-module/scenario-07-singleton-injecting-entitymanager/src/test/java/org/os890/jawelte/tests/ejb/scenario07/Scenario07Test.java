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
package org.os890.jawelte.tests.ejb.scenario07;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * TICKET-007 scenario 7 — {@code @Inject EntityManager} on a
 * {@code @Singleton} resolves through jpa-module's per-tx proxy.
 * The same {@code @Transactional} method writes then reads; the
 * read observes the un-flushed insert, proving the proxy routed
 * both calls through the active per-tx EM.
 */
@EnableTestBeans
class Scenario07Test {

    @Inject
    NoteService notes;

    @Test
    void injectedEntityManagerProxyRoutesToActivePerTxEm() {
        long count = notes.saveAndReadInSameTx("inside-tx");
        assertThat(count).as("the read inside the same tx must see the un-flushed insert").isEqualTo(1L);
    }
}
