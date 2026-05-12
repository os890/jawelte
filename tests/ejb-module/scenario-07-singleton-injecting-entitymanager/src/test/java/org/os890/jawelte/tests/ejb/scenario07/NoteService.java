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

import jakarta.ejb.Singleton;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

/**
 * {@code @Singleton} bean with {@code @Inject EntityManager em}.
 * The injected EM is the jpa-module-supplied proxy that routes to
 * the active per-tx instance — observable by writing and reading
 * inside the same transactional method: the un-flushed insert is
 * visible to the subsequent query.
 */
@Singleton
public class NoteService {

    @Inject
    private EntityManager entityManager;

    /** Required public no-arg constructor. */
    public NoteService() {
    }

    /**
     * Persist a note and immediately query the row count inside the
     * same {@code @Transactional} method. The proxy must route both
     * the persist and the query through the SAME per-tx EM for the
     * read to see the un-flushed insert.
     *
     * @param body the body to persist
     * @return the row count observed after the persist
     */
    public long saveAndReadInSameTx(String body) {
        entityManager.persist(new Note(body));
        return entityManager
                .createQuery("SELECT COUNT(n) FROM Note n", Long.class)
                .getSingleResult();
    }
}
