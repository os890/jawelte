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
package org.os890.jawelte.tests.jpa.scenario72;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.transaction.Transactional;

import org.os890.jawelte.module.jpa.api.ReadOnly;

/**
 * Nested {@code @ReadOnly} level that runs in its own transaction and
 * touches PU-a. When it returns it exits <em>one</em> read-only nesting
 * level; the enclosing {@code @ReadOnly} level must remain active.
 */
@ApplicationScoped
public class NestedReadOnlyService {

    @Inject
    @Named("testPU72a")
    private EntityManager entityManagerA;

    /** Default constructor for CDI. */
    public NestedReadOnlyService() {
    }

    /**
     * Dereference PU-a inside a nested {@code @ReadOnly} transaction.
     *
     * @return PU-a's flush mode inside the nested read-only tx
     */
    @Transactional
    @ReadOnly
    public FlushModeType touchPuA() {
        return entityManagerA.getFlushMode();
    }
}
