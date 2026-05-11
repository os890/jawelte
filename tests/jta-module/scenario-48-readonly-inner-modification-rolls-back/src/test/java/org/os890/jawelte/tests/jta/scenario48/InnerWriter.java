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
package org.os890.jawelte.tests.jta.scenario48;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.os890.jawelte.module.jpa.api.ReadOnly;

/**
 * Inner CDI bean called by {@link OuterWriter}. Persists an
 * {@link Item} inside a {@code REQUIRES_NEW @ReadOnly} transaction —
 * {@code ReadOnlyInterceptor} marks the inner JTA tx
 * {@code rollback-only}, so the persist is discarded at JTA commit
 * time. Lives in a separate bean so the {@code @Transactional}
 * interceptor actually fires on the boundary call.
 */
@ApplicationScoped
public class InnerWriter {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public InnerWriter() {
    }

    /**
     * Attempt to persist inside a {@code @ReadOnly} REQUIRES_NEW
     * transaction. Returns normally; the inner JTA tx is marked
     * {@code rollback-only} so the persist is undone at commit time.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @ReadOnly
    public void persistInsideReadOnlyInner() {
        entityManager.persist(new Item());
    }
}
