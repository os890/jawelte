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
import jakarta.persistence.EntityManager;

/**
 * No {@code @Transactional} of its own — relies on the calling
 * bean's active transaction. Holds the {@code @Inject EntityManager}
 * and does the actual JPA work.
 */
@ApplicationScoped
public class InnerService {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public InnerService() {
    }

    /** Persist + flush a Marker against the active transaction. */
    public void persistMarker() {
        entityManager.persist(new Marker());
        entityManager.flush();
    }

    /**
     * Count rows.
     *
     * @return the row count visible to the active transaction
     */
    public long countMarkers() {
        return entityManager
                .createQuery("SELECT COUNT(m) FROM Marker m", Long.class)
                .getSingleResult();
    }
}
