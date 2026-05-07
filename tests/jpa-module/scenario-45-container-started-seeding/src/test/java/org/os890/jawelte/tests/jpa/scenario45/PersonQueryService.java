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
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * {@code @Transactional} CDI service bean — wraps queries so the
 * interceptor fires (a {@code @Transactional} annotation on a
 * JUnit {@code @Test} method does not fire today; tracked as a
 * separate impl task).
 */
@ApplicationScoped
public class PersonQueryService {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public PersonQueryService() {
    }

    /**
     * Count people with the given name.
     *
     * @param name the name to match
     * @return the number of matching rows
     */
    @Transactional
    public long countByName(String name) {
        return entityManager
                .createQuery("SELECT COUNT(p) FROM Person p WHERE p.name = :n", Long.class)
                .setParameter("n", name)
                .getSingleResult();
    }

    /**
     * Total {@code Person} row count.
     *
     * @return the row count
     */
    @Transactional
    public long countAll() {
        return entityManager
                .createQuery("SELECT COUNT(p) FROM Person p", Long.class)
                .getSingleResult();
    }
}
