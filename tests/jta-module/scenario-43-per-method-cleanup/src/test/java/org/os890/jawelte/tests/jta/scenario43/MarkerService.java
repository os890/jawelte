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
package org.os890.jawelte.tests.jta.scenario43;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Plain {@code @Transactional} service for the per-method-cleanup scenario. */
@ApplicationScoped
public class MarkerService {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public MarkerService() {
    }

    /** Persist one row. */
    @Transactional
    public void persistOne() {
        entityManager.persist(new Marker());
    }

    /**
     * Return the row count.
     *
     * @return the count
     */
    @Transactional
    public long count() {
        return entityManager.createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                .getSingleResult();
    }
}
