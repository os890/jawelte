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
package org.os890.jawelte.tests.jpa.scenario55;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.os890.jawelte.module.jpa.api.ReadOnly;

/**
 * Inner layer: nested {@code @Transactional @ReadOnly} method.
 * Persists a new {@link Note} and mutates an existing one — both
 * must roll back when the inner tx ends, leaving the outer tx
 * unaffected.
 */
@ApplicationScoped
public class InnerReadOnlyService {

    @Inject
    private EntityManager entityManager;

    /** Default constructor required by CDI. */
    public InnerReadOnlyService() {
    }

    /**
     * Inside a fresh inner tx scope: insert a new {@link Note} and
     * mutate an existing one's text. The {@code @ReadOnly}
     * interceptor sets {@code FlushMode.COMMIT} and marks the inner
     * tx rollback-only, so neither change reaches the database.
     *
     * @param idToMutate id of the existing note to setter-mutate
     * @param newText    the (discarded) replacement text
     */
    @Transactional
    @ReadOnly
    public void mutateAndInsertUnderReadOnly(Long idToMutate, String newText) {
        entityManager.persist(new Note("inner-readonly-insert"));
        Note existing = entityManager.find(Note.class, idToMutate);
        existing.setText(newText);
    }
}
