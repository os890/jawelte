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
package org.os890.jawelte.tests.jpa.scenario51;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/** Persists a 2-level hierarchy and counts rows. */
@ApplicationScoped
public class PersonHierarchyService {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public PersonHierarchyService() {
    }

    /**
     * Persist a parent and a child whose parent is the new root.
     */
    @Transactional
    public void persistTwoLevelHierarchy() {
        Person root = new Person("root");
        entityManager.persist(root);
        Person child = new Person("child", root);
        entityManager.persist(child);
    }

    /**
     * Total {@link Person} row count.
     *
     * @return the row count
     */
    @Transactional
    public long countPeople() {
        return entityManager
                .createQuery("SELECT COUNT(p) FROM Person p", Long.class)
                .getSingleResult();
    }
}
