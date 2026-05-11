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
package org.os890.jawelte.tests.ejb.scenario05;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * TICKET-007 scenario 5 — implicit {@code @Transactional} on a
 * {@code @jakarta.ejb.Singleton} bean. {@code save(...)} carries no
 * explicit transactional annotation; jpa-module's
 * {@code TransactionalInterceptor} sees the class-level
 * {@code @Transactional} ejb-module added and auto-commits.
 */
@EnableTestBeans
class Scenario05Test {

    @Inject
    NoteRepository notes;

    @Test
    void implicitTransactionalOnSingletonCommitsPersist() {
        Long id = notes.save("hello-ejb");
        assertThat(id).as("commit must assign a generated id").isNotNull();

        long count = notes.count();
        assertThat(count).as("a follow-up tx must observe the committed row").isEqualTo(1L);
    }
}
