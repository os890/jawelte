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
package org.os890.jawelte.tests.jta.scenario54;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** {@code @Transactional} CDI service backed by a Geronimo-driven JTA tx. */
@ApplicationScoped
public class GeronimoMarkerService {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public GeronimoMarkerService() {
    }

    /**
     * Persist a new {@link GeronimoMarker} and return its assigned id.
     *
     * @return the persisted id
     */
    @Transactional
    public Long createMarker() {
        GeronimoMarker marker = new GeronimoMarker();
        entityManager.persist(marker);
        return marker.getId();
    }

    /**
     * Total row count.
     *
     * @return the row count
     */
    @Transactional
    public long countMarkers() {
        return entityManager
                .createQuery("SELECT COUNT(m) FROM GeronimoMarker m", Long.class)
                .getSingleResult();
    }
}
