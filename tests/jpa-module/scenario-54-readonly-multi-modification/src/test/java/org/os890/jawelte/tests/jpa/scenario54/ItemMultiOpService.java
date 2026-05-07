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
package org.os890.jawelte.tests.jpa.scenario54;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import org.os890.jawelte.module.jpa.api.ReadOnly;

/**
 * Drives a multi-op {@code @ReadOnly @Transactional} call: insert,
 * setter mutation, and remove in one body. Reads back the
 * post-call DB state so the test can verify nothing leaked.
 */
@ApplicationScoped
public class ItemMultiOpService {

    @Inject
    private EntityManager entityManager;

    /** Default constructor required by CDI. */
    public ItemMultiOpService() {
    }

    /**
     * Persist a new {@link Item} and return its id.
     *
     * @param name the item name
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
     * Within a single {@code @ReadOnly @Transactional} method:
     * (1) insert a brand-new {@code Item}, (2) mutate an existing
     * one via setter, (3) remove another existing one. All three
     * modifications must be discarded on tx-end (rollback-only +
     * {@code FlushMode.COMMIT}).
     *
     * @param idToMutate id of the item to setter-mutate
     * @param newName    the (discarded) replacement name
     * @param idToRemove id of the item to remove
     */
    @Transactional
    @ReadOnly
    public void multiModificationUnderReadOnly(Long idToMutate, String newName, Long idToRemove) {
        entityManager.persist(new Item("inserted-but-rolled-back"));
        Item toMutate = entityManager.find(Item.class, idToMutate);
        toMutate.setName(newName);
        Item toRemove = entityManager.find(Item.class, idToRemove);
        entityManager.remove(toRemove);
    }

    /**
     * Total {@link Item} row count.
     *
     * @return the count
     */
    @Transactional
    public long countItems() {
        return entityManager
                .createQuery("SELECT COUNT(i) FROM Item i", Long.class)
                .getSingleResult();
    }

    /**
     * Read the current name of an item, or {@code null} if removed.
     *
     * @param id the item id
     * @return the persisted name, or {@code null}
     */
    @Transactional
    public String currentName(Long id) {
        Item item = entityManager.find(Item.class, id);
        return item == null ? null : item.getName();
    }
}
