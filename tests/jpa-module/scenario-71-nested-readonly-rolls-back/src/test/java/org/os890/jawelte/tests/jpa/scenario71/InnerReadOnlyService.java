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
 * Inner layer: a nested {@code @Transactional @ReadOnly} method.
 * Because the framework starts a fresh transaction for every
 * {@code @Transactional} invocation, this inner call must roll back
 * its own writes on its own frame — independently of the outer
 * {@code @ReadOnly} level.
 */
@ApplicationScoped
public class InnerReadOnlyService {

    @Inject
    private EntityManager entityManager;

    /** Default constructor required by CDI. */
    public InnerReadOnlyService() {
    }

    /**
     * Persist a {@link Note} inside a nested {@code @ReadOnly}
     * transaction and report the flush mode seen inside the body.
     *
     * @param text the (discarded) text to insert
     * @return the flush mode of the inner transaction's EntityManager
     */
    @Transactional
    @ReadOnly
    public FlushModeType insertUnderReadOnly(String text) {
        entityManager.persist(new Note(text));
        return entityManager.getFlushMode();
    }
}
