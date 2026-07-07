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
package org.os890.jawelte.tests.jpa.scenario70;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.transaction.Transactional;

import org.os890.jawelte.module.jpa.api.ReadOnly;

/**
 * A {@code @ReadOnly} method running in its OWN ({@code REQUIRES_NEW})
 * transaction. Used to verify that its read-only flush mode does not leak
 * into the enclosing writable transaction.
 */
@ApplicationScoped
public class NestedReadOnly {

    @Inject
    @Named("testPU70b")
    private EntityManager lazyEntityManager;

    /** Default constructor for CDI. */
    public NestedReadOnly() {
    }

    /**
     * @return the flush mode of PU-b's EM inside this nested @ReadOnly tx
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @ReadOnly
    public FlushModeType lazyPuFlushMode() {
        return lazyEntityManager.getFlushMode();
    }
}
