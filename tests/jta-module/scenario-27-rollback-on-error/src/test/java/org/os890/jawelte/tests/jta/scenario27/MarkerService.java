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
package org.os890.jawelte.tests.jta.scenario27;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** {@code @Transactional} service that persists then throws {@link Error}. */
@ApplicationScoped
public class MarkerService {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public MarkerService() {
    }

    /**
     * Persist a {@link Marker}, then throw an {@link Error}.
     * The project's interceptor convention treats every throwable as
     * a rollback signal, including {@code Error} subtypes.
     */
    @Transactional
    public void persistAndError() {
        entityManager.persist(new Marker());
        throw new AssertionError("scenario-27 — intentional Error rollback driver");
    }

    /**
     * Return the row count.
     *
     * @return the count
     */
    @Transactional
    public long countMarkers() {
        return entityManager.createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                .getSingleResult();
    }
}
