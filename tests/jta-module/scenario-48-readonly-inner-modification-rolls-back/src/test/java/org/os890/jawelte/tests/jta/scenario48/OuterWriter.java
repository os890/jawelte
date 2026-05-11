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

/**
 * Outer CDI bean that persists an {@link Item} inside its own
 * writable {@code @Transactional} method, then delegates to
 * {@link InnerWriter} whose persist runs in a separate suspended /
 * resumed JTA transaction that gets rolled back by
 * {@code @ReadOnly}. Outer's persist must survive.
 */
@ApplicationScoped
public class OuterWriter {

    @Inject
    private EntityManager entityManager;

    @Inject
    private InnerWriter innerWriter;

    /** No-arg constructor for CDI. */
    public OuterWriter() {
    }

    /**
     * Writable outer transaction: persist one {@link Item}, then
     * delegate to the inner read-only writer (which attempts a
     * persist that the inner JTA tx rolls back).
     */
    @Transactional
    public void persistThenInnerReadOnlyPersist() {
        entityManager.persist(new Item());
        innerWriter.persistInsideReadOnlyInner();
    }

    /**
     * Count rows after the outer transaction has completed — a
     * separate {@code @Transactional} call so the assertion sees the
     * committed state.
     *
     * @return the row count
     */
    @Transactional
    public long countCommittedItems() {
        return entityManager.createQuery("SELECT COUNT(i) FROM Item i", Long.class)
                .getSingleResult();
    }
}
