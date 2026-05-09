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
package org.os890.jawelte.tests.jpa.scenario65;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * {@code @Transactional} entry point used by the test method (which
 * is itself NOT {@code @Transactional}). Carries no
 * {@code EntityManager} field — the strategy's
 * {@code begin(targetClass)} sees zero EM candidates on
 * {@code OuterService} and falls through to the single-PU shortcut
 * (the only PU testPU65 is eagerly opened). The actual JPA work
 * happens in {@link InnerService}, which has the
 * {@code @Inject EntityManager} field; its proxy resolves to the
 * same active EM under the propagated tx.
 */
@ApplicationScoped
public class OuterService {

    @Inject
    private InnerService innerService;

    /** No-arg constructor for CDI. */
    public OuterService() {
    }

    /** {@code @Transactional} wrapper that delegates persistence to {@link InnerService}. */
    @Transactional
    public void persistViaInner() {
        innerService.persistMarker();
    }

    /**
     * {@code @Transactional} wrapper that delegates the count query
     * to {@link InnerService}. Used by the test for the verification
     * step instead of opening its own tx.
     *
     * @return the row count
     */
    @Transactional
    public long countViaInner() {
        return innerService.countMarkers();
    }
}
