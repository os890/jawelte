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
package org.os890.jawelte.tests.jta.scenario41;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.os890.jawelte.module.jpa.api.ReadOnly;

/** {@code @ReadOnly} service against PU "a" in a multi-PU CDI container. */
@ApplicationScoped
public class MultiPuReadOnlyService {

    @Inject
    @Named("testJtaPU41a")
    private EntityManager entityManagerA;

    /** No-arg constructor for CDI. */
    public MultiPuReadOnlyService() {
    }

    /**
     * Persist a row inside a {@code @ReadOnly} JTA tx — must be
     * rolled back at commit time.
     */
    @Transactional
    @ReadOnly
    public void persistInsideReadOnly() {
        entityManagerA.persist(new MarkerA());
    }

    /**
     * Plain count query inside {@code @ReadOnly} — works.
     *
     * @return PU "a"'s row count
     */
    @Transactional
    @ReadOnly
    public long countInPuA() {
        return entityManagerA.createQuery("SELECT COUNT(m) FROM MarkerA m", Long.class)
                .getSingleResult();
    }
}
