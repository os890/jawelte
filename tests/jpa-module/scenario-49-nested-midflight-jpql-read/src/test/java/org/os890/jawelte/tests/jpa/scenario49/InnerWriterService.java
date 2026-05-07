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
package org.os890.jawelte.tests.jpa.scenario49;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Inner service: persists a row and commits before returning. */
@ApplicationScoped
public class InnerWriterService {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public InnerWriterService() {
    }

    /**
     * Persist a {@link Person} on a fresh inner-level EM/tx.
     *
     * @param name the person's name
     */
    @Transactional
    public void persist(String name) {
        entityManager.persist(new Person(name));
    }
}
