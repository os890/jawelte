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

import jakarta.ejb.Singleton;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * {@code @Singleton} repository with a method-level
 * {@code @TransactionAttribute(REQUIRES_NEW)} that ejb-module
 * silently ignores. The class-level {@code @Transactional} the
 * default mapper added still applies — the method runs with the
 * default {@code TxType.REQUIRED} semantics. {@code REQUIRES_NEW}
 * has no observable effect (no nested-tx isolation, no separate
 * commit boundary).
 */
@Singleton
public class NoteRepository {

    @Inject
    private EntityManager entityManager;

    /** Required public no-arg constructor. */
    public NoteRepository() {
    }

    /**
     * Save a note. The {@code @TransactionAttribute(REQUIRES_NEW)}
     * is on the method; ejb-module ignores it. The method still
     * runs in a transaction (the implicit {@code @Transactional}
     * at the class level), so the persist commits.
     *
     * @param body the body to persist
     * @return the assigned id
     */
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Long save(String body) {
        Note note = new Note(body);
        entityManager.persist(note);
        return note.getId();
    }

    /**
     * @return the row count
     */
    public long count() {
        return entityManager
                .createQuery("SELECT COUNT(n) FROM Note n", Long.class)
                .getSingleResult();
    }
}
