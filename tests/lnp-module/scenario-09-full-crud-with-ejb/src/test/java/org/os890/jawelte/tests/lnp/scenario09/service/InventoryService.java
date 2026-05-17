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
package org.os890.jawelte.tests.lnp.scenario09.service;

import jakarta.ejb.Stateless;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import org.os890.jawelte.tests.lnp.scenario09.entity.inventory.StockItem;

/**
 * Inventory domain service — {@code @Stateless} EJB. Method names
 * mirror scenario-01's inventory test methods.
 */
@Stateless
public class InventoryService {

    @Inject
    private EntityManager em;

    /** No-arg constructor required by the EJB stereotype. */
    public InventoryService() {
    }

    /** SELECT si FROM StockItem si WHERE si.warehouse.id = :id. */
    public void queryStockByWarehouse(Long warehouseId) {
        em.createQuery(
                "SELECT si FROM StockItem si WHERE si.warehouse.id = :id",
                StockItem.class)
                .setParameter("id", warehouseId)
                .getResultList();
    }

    /** SELECT SUM(si.quantity) FROM StockItem si. */
    public void totalStockQuantity() {
        em.createQuery(
                "SELECT SUM(si.quantity) FROM StockItem si", Long.class)
                .getSingleResult();
    }

    /** Add {@code delta} to stock item N's quantity. */
    public void updateStockQuantity(Long stockId, int delta) {
        StockItem si = em.find(StockItem.class, stockId);
        si.setQuantity(si.getQuantity() + delta);
        em.flush();
    }
}
