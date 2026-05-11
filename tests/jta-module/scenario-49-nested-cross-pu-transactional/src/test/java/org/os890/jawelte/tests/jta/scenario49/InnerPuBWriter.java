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
package org.os890.jawelte.tests.jta.scenario49;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Inner CDI bean called by {@link OuterPuAWriter}. Persists into
 * PU "b" inside a nested {@code @Transactional} call. Lives in a
 * separate bean so the {@code @Transactional} interceptor actually
 * fires on the boundary call.
 */
@ApplicationScoped
public class InnerPuBWriter {

    @Inject
    @Named("testJtaPU49b")
    private EntityManager entityManagerB;

    /** No-arg constructor for CDI. */
    public InnerPuBWriter() {
    }

    /** Persist a {@link MarkerB} into PU "b". */
    @Transactional
    public void persistMarkerB() {
        entityManagerB.persist(new MarkerB());
    }

    /**
     * Read-only count over PU "b" — used by the test to verify the
     * commit.
     *
     * @return row count
     */
    @Transactional
    public long countMarkerB() {
        return entityManagerB.createQuery("SELECT COUNT(m) FROM MarkerB m", Long.class)
                .getSingleResult();
    }
}
