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
 * Outer CDI bean: writes a {@link MarkerA} into PU "a" and then
 * delegates to {@link InnerPuBWriter} which writes a {@link MarkerB}
 * into PU "b" inside a nested {@code @Transactional} call.
 */
@ApplicationScoped
public class OuterPuAWriter {

    @Inject
    @Named("testJtaPU49a")
    private EntityManager entityManagerA;

    @Inject
    private InnerPuBWriter innerPuBWriter;

    /** No-arg constructor for CDI. */
    public OuterPuAWriter() {
    }

    /**
     * Persist a row into PU "a", then call the inner method that
     * persists a row into PU "b". Both writes are expected to commit.
     */
    @Transactional
    public void persistAcrossBothPus() {
        entityManagerA.persist(new MarkerA());
        innerPuBWriter.persistMarkerB();
    }

    /**
     * Read-only count over PU "a" — used by the test to verify the
     * commit.
     *
     * @return row count
     */
    @Transactional
    public long countMarkerA() {
        return entityManagerA.createQuery("SELECT COUNT(m) FROM MarkerA m", Long.class)
                .getSingleResult();
    }
}
