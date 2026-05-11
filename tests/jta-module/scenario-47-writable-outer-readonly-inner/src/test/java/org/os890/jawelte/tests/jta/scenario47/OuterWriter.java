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
package org.os890.jawelte.tests.jta.scenario47;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Outer CDI bean that writes inside a {@code @Transactional} method,
 * then calls {@link InnerReader#readItemCount()} which runs in its
 * own {@code REQUIRES_NEW} {@code @ReadOnly} transaction.
 */
@ApplicationScoped
public class OuterWriter {

    @Inject
    private EntityManager entityManager;

    @Inject
    private InnerReader innerReader;

    /** No-arg constructor for CDI. */
    public OuterWriter() {
    }

    /**
     * Writable outer transaction that persists an {@link Item}, then
     * delegates to the {@code REQUIRES_NEW} {@code @ReadOnly} inner.
     * The outer's persist must survive regardless of the inner's
     * read-only marking — the inner's rollback applies to its own
     * suspended-and-resumed JTA tx, not the outer's.
     *
     * @return the count of items observed by the inner read-only tx
     */
    @Transactional
    public long persistThenInnerRead() {
        entityManager.persist(new Item());
        return innerReader.readItemCount();
    }

    /**
     * Count rows in the persistence unit after the test's outer
     * transaction has completed — separate {@code @Transactional}
     * call so the assertion sees the committed state.
     *
     * @return the row count
     */
    @Transactional
    public long countCommittedItems() {
        return entityManager.createQuery("SELECT COUNT(i) FROM Item i", Long.class)
                .getSingleResult();
    }
}
