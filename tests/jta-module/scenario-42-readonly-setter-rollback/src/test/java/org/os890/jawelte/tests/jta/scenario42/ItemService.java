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
package org.os890.jawelte.tests.jta.scenario42;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.os890.jawelte.module.jpa.api.ReadOnly;

/** Mixed writable / read-only service for the setter-rollback scenario. */
@ApplicationScoped
public class ItemService {

    @Inject
    private EntityManager entityManager;

    /** No-arg constructor for CDI. */
    public ItemService() {
    }

    /**
     * Plain {@code @Transactional} create — commits.
     *
     * @param name the new item's name
     */
    @Transactional
    public void createItem(String name) {
        entityManager.persist(new Item(name));
    }

    /**
     * Load an existing item and modify its name via setter inside a
     * {@code @ReadOnly} JTA tx. Hibernate's dirty-check would
     * propagate the change at flush-time, but the
     * {@code ReadOnlyInterceptor} marks the tx rollback-only — the
     * change must be discarded at JTA commit.
     *
     * @param currentName the existing item's name
     * @param newName     the modified name (should be discarded)
     */
    @Transactional
    @ReadOnly
    public void renameInReadOnly(String currentName, String newName) {
        entityManager.createQuery("SELECT i FROM Item i WHERE i.name = :n", Item.class)
                .setParameter("n", currentName)
                .getResultList()
                .stream()
                .findFirst()
                .ifPresent(item -> item.setName(newName));
    }

    /**
     * Read the name of an existing item.
     *
     * @param name the item's name
     * @return the item's current name (which equals the input when
     *         the row exists, {@code null} otherwise)
     */
    @Transactional
    public String findName(String name) {
        return entityManager.createQuery("SELECT i FROM Item i WHERE i.name = :n", Item.class)
                .setParameter("n", name)
                .getResultList()
                .stream()
                .findFirst()
                .map(Item::getName)
                .orElse(null);
    }
}
