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
package org.os890.jawelte.tests.jta.scenario35;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.os890.jawelte.module.jpa.api.ReadOnly;

/** {@code @ReadOnly} service that performs multiple modifications. */
@ApplicationScoped
public class ItemMultiOpService {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public ItemMultiOpService() {
    }

    /**
     * Perform several persists inside one {@code @ReadOnly @Transactional}
     * method. The interceptor sets rollback-only at entry; every
     * write must be discarded at JTA commit time.
     */
    @Transactional
    @ReadOnly
    public void persistMany(int count) {
        for (int i = 0; i < count; i++) {
            entityManager.persist(new Item());
        }
    }

    /**
     * Return the row count.
     *
     * @return the count
     */
    @Transactional
    public long countItems() {
        return entityManager.createQuery("SELECT COUNT(i) FROM Item i", Long.class)
                .getSingleResult();
    }
}
