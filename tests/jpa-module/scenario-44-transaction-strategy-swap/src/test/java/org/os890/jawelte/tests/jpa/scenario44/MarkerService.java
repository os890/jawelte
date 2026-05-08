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
package org.os890.jawelte.tests.jpa.scenario44;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Service-bean wrapper so the @Transactional CDI interceptor fires (it doesn't fire on @Test methods through this path). */
@ApplicationScoped
public class MarkerService {

    @Inject
    private EntityManager entityManager;

    /** Default constructor for CDI. */
    public MarkerService() {
    }

    /** Persist a {@link Marker} inside a @Transactional boundary. */
    @Transactional
    public void persistMarker() {
        entityManager.persist(new Marker());
        entityManager.flush();
    }
}
