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
package org.os890.jawelte.module.jpa.impl.adapter.connection;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.enterprise.event.Observes;
import jakarta.persistence.EntityManager;
import jakarta.transaction.TransactionScoped;

/**
 * {@code @TransactionScoped} bean that observes
 * {@link EntityManagerCreatedEvent}s fired by {@code JpaCdiExtension}'s
 * JTA-mode synthetic {@code EntityManager} bean. Each captured
 * {@link EntityManager} is stored under its persistence unit name so
 * {@code DefaultPersistenceUnitConnectionResolver.connectionFor(...)}
 * can retrieve the <em>raw</em> EM (not a CDI client proxy) and call
 * {@code em.unwrap(org.hibernate.Session.class).doReturningWork(...)}
 * without running into Weld's proxy-identity shortcut on
 * {@code unwrap} returning {@code this}.
 *
 * <p>Because the bean's lifetime is bound to the active JTA
 * transaction, its captured EMs are valid for exactly the
 * transaction during which they were produced — which is also the
 * window in which the seed / diff transaction template needs the
 * connection. No cross-transaction leakage.
 *
 * <p>Discovered by jpa-module/impl's
 * {@code bean-discovery-mode="annotated"} via the
 * {@link TransactionScoped} bean-defining annotation.
 */
@TransactionScoped
public class JtaEntityManagerCapture implements java.io.Serializable {

    /** Serialization version for the (rarely passivated) JTA bean. */
    private static final long serialVersionUID = 1L;

    /**
     * Persistence-unit-name → raw {@code EntityManager}. {@code transient}
     * because {@code EntityManager} is not {@code Serializable} — the bean
     * is never passivated in jawelte's test scenarios (an active JTA tx
     * keeps it alive), so post-deserialize emptiness is acceptable.
     */
    private final transient Map<String, EntityManager> byPersistenceUnit = new HashMap<>();

    /** Default no-arg constructor required by CDI. */
    public JtaEntityManagerCapture() {
    }

    /**
     * Capture the raw {@link EntityManager} the synthetic JTA bean
     * just produced. Called by CDI when {@code JpaCdiExtension}
     * fires the event from its {@code produceWith} lambda.
     *
     * @param event the fired {@link EntityManagerCreatedEvent}
     */
    void onEntityManagerCreated(@Observes EntityManagerCreatedEvent event) {
        byPersistenceUnit.put(event.persistenceUnitName(), event.entityManager());
    }

    /**
     * Look up the raw {@link EntityManager} captured for the given
     * persistence unit within the current JTA transaction.
     *
     * @param persistenceUnitName the persistence unit name
     * @return the captured {@code EntityManager}, or
     *         {@link Optional#empty()} if none was captured in the
     *         current JTA transaction
     */
    public Optional<EntityManager> forPersistenceUnit(String persistenceUnitName) {
        return Optional.ofNullable(byPersistenceUnit.get(persistenceUnitName));
    }
}
