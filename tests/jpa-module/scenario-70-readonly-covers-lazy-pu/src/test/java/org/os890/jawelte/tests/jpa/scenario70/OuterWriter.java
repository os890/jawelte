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

/**
 * Writable outer transaction that first calls a nested {@code REQUIRES_NEW}
 * {@code @ReadOnly} method and then dereferences its own PU-b
 * {@link EntityManager}. The nested read-only scope must not make the outer
 * (enclosing) EM read-only.
 */
@ApplicationScoped
public class OuterWriter {

    @Inject
    private NestedReadOnly nestedReadOnly;

    @Inject
    @Named("testPU70b")
    private EntityManager lazyEntityManager;

    /** Default constructor for CDI. */
    public OuterWriter() {
    }

    /**
     * @return {@code [nestedFlushMode, outerFlushMode]} — the nested
     *         @ReadOnly EM's mode and the enclosing writable EM's mode
     */
    @Transactional
    public FlushModeType[] nestedThenOuterFlushModes() {
        FlushModeType nested = nestedReadOnly.lazyPuFlushMode();
        FlushModeType outer = lazyEntityManager.getFlushMode();
        return new FlushModeType[] {nested, outer};
    }
}
