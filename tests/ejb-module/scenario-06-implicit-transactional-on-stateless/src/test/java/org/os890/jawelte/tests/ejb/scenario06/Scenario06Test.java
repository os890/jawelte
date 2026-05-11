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
package org.os890.jawelte.tests.ejb.scenario06;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * TICKET-007 scenario 6 — implicit {@code @Transactional} on a
 * {@code @jakarta.ejb.Stateless} bean. Same shape as scenario 5
 * but with a {@code @Stateless} subject; auto-commit still works.
 */
@EnableTestBeans
class Scenario06Test {

    @Inject
    NoteRepository notes;

    @Test
    void implicitTransactionalOnStatelessCommitsPersist() {
        Long id = notes.save("hello-stateless");
        assertThat(id).isNotNull();
        assertThat(notes.count()).isEqualTo(1L);
    }
}
