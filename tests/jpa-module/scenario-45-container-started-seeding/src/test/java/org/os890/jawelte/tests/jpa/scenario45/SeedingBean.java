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
package org.os890.jawelte.tests.jpa.scenario45;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.os890.jawelte.core.api.event.ContainerStarted;

/**
 * Observes {@link ContainerStarted} and seeds a reference
 * {@link Person} via {@code @Transactional} + {@link EntityManager}.
 * The first {@code @Test} method sees the seeded row; per-method
 * cleanup wipes it for subsequent methods.
 */
@ApplicationScoped
public class SeedingBean {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public SeedingBean() {
    }

    /**
     * Observer fired during the bootstrap window. The
     * {@code @Transactional} interceptor wraps this method's body so
     * the persist + commit succeed before the first
     * {@code @Test} method runs.
     *
     * @param event the {@link ContainerStarted} event
     */
    @Transactional
    public void onContainerStarted(@Observes ContainerStarted event) {
        Person seedPerson = new Person("seed-Alice");
        entityManager.persist(seedPerson);
    }
}
