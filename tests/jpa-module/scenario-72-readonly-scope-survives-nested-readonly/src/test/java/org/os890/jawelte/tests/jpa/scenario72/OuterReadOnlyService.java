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
 * Outer {@code @ReadOnly} method in an all-lazy multi-PU frame. It first
 * calls a nested {@code @ReadOnly} method (which enters and exits one
 * read-only nesting level), then dereferences PU-b for the first time.
 * Because the read-only scope is depth-tracked, the nested call's return
 * must not end the scope: PU-b, lazily joined afterwards, must still be
 * {@link FlushModeType#COMMIT}.
 */
@ApplicationScoped
public class OuterReadOnlyService {

    @Inject
    private NestedReadOnlyService nestedReadOnlyService;

    @Inject
    @Named("testPU72b")
    private EntityManager entityManagerB;

    /** Default constructor for CDI. */
    public OuterReadOnlyService() {
    }

    /**
     * Run the nested {@code @ReadOnly} call, then dereference PU-b for
     * the first time and report its flush mode.
     *
     * @return {@code [nestedPuAMode, lazyPuBModeAfterNested]}
     */
    @Transactional
    @ReadOnly
    public FlushModeType[] nestedThenLazyPuFlushModes() {
        FlushModeType nested = nestedReadOnlyService.touchPuA();
        FlushModeType lazyAfterNested = entityManagerB.getFlushMode();
        return new FlushModeType[] {nested, lazyAfterNested};
    }
}
