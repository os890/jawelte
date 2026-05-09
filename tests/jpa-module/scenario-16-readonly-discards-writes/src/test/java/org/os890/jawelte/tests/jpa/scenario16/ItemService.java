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
package org.os890.jawelte.tests.jpa.scenario16;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.os890.jawelte.module.jpa.api.ReadOnly;

/** Seed, mutate-via-setter under @ReadOnly, and read back. */
@ApplicationScoped
public class ItemService {

    @Inject
    private EntityManager entityManager;

    /** Default constructor required by CDI. */
    public ItemService() {
    }

    /**
     * Persist a new {@link Item} with the given name.
     *
     * @param name the initial name
     * @return the assigned id
     */
    @Transactional
    public Long seed(String name) {
        Item item = new Item(name);
        entityManager.persist(item);
        entityManager.flush();
        return item.getId();
    }

    /**
     * Inside an {@code @ReadOnly @Transactional} method: load the
     * managed item and mutate it via setter — no explicit
     * {@code persist} / {@code merge} / {@code flush}. The setter
     * dirty mark must NOT reach the DB.
     *
     * @param id      the entity id
     * @param newName the (discarded) new name
     */
    @Transactional
    @ReadOnly
    public void mutateViaSetterUnderReadOnly(Long id, String newName) {
        Item item = entityManager.find(Item.class, id);
        item.setName(newName);
    }

    /**
     * Read the current persisted name.
     *
     * @param id the entity id
     * @return the name as currently stored
     */
    @Transactional
    public String currentName(Long id) {
        return entityManager.find(Item.class, id).getName();
    }

    /**
     * Read-only JPQL lookup by name. Mirrors POC's
     * {@code ReadOnlyService.findByName} (used in Order 2): a
     * {@code @ReadOnly @Transactional} query path proves that
     * read-only mode does not interfere with normal queries.
     *
     * @param name the item name to look up
     * @return the matching item, or {@code null} if none
     */
    @Transactional
    @ReadOnly
    public Item findByName(String name) {
        return entityManager
                .createQuery("SELECT i FROM Item i WHERE i.name = :name", Item.class)
                .setParameter("name", name)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }
}
