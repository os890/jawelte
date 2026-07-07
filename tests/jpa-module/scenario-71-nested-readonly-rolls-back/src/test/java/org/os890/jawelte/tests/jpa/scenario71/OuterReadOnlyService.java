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
package org.os890.jawelte.tests.jpa.scenario71;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.transaction.Transactional;

import org.os890.jawelte.module.jpa.api.ReadOnly;

/**
 * Outer layer: a {@code @Transactional @ReadOnly} method that writes,
 * calls a nested {@code @ReadOnly} method, and writes again after the
 * nested call returns. All of its own writes plus the nested call's
 * writes must be discarded, and the outer level must remain read-only
 * across the nested call's return.
 */
@ApplicationScoped
public class OuterReadOnlyService {

    @Inject
    private EntityManager entityManager;

    @Inject
    private InnerReadOnlyService innerReadOnlyService;

    /** Default constructor required by CDI. */
    public OuterReadOnlyService() {
    }

    /**
     * Persist a {@link Note}, invoke the nested {@code @ReadOnly}
     * insert, then persist a second {@link Note}. Nothing may reach
     * the database: the outer frame is marked rollback-only by its own
     * {@code @ReadOnly}, and the inner frame by its own.
     *
     * @param outerText the (discarded) text written by the outer level
     * @param innerText the (discarded) text written by the inner level
     */
    @Transactional
    @ReadOnly
    public void outerAndNestedReadOnlyWrites(String outerText, String innerText) {
        entityManager.persist(new Note(outerText + "-before"));
        innerReadOnlyService.insertUnderReadOnly(innerText);
        entityManager.persist(new Note(outerText + "-after"));
    }

    /**
     * Invoke the nested {@code @ReadOnly} call, then report the outer
     * EntityManager's flush mode after it returns. Proves the outer
     * level is still read-only ({@code COMMIT}) once the inner level
     * has unwound.
     *
     * @param innerText the (discarded) text the nested call writes
     * @return the outer EntityManager's flush mode after the nested call
     */
    @Transactional
    @ReadOnly
    public FlushModeType outerFlushModeAfterNestedReadOnly(String innerText) {
        innerReadOnlyService.insertUnderReadOnly(innerText);
        return entityManager.getFlushMode();
    }

    /**
     * Persist a {@link Note} in a plain writable transaction.
     *
     * @param text the text to persist
     */
    @Transactional
    public void seed(String text) {
        entityManager.persist(new Note(text));
    }

    /**
     * Total {@link Note} row count.
     *
     * @return the count
     */
    @Transactional
    public long countNotes() {
        return entityManager
                .createQuery("SELECT COUNT(n) FROM Note n", Long.class)
                .getSingleResult();
    }
}
