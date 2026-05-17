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

import jakarta.persistence.EntityManager;

/**
 * Internal CDI event fired by {@code JpaCdiExtension}'s JTA-mode
 * synthetic {@code EntityManager} bean immediately after
 * {@code EntityManagerFactory.createEntityManager()} returns. The
 * payload is the <em>raw</em> {@link EntityManager} (not the CDI
 * client proxy a caller would see when injecting), so observers can
 * call provider-specific methods like
 * {@code em.unwrap(org.hibernate.Session.class)} without tripping
 * Weld's proxy-identity shortcut on {@code unwrap}-returns-{@code this}.
 *
 * <p>The {@link JtaEntityManagerCapture} {@code @TransactionScoped}
 * observer stores the EM under the supplied persistence unit name so
 * {@code DefaultPersistenceUnitConnectionResolver.connectionFor(...)}
 * can look it up without going through CDI's client proxy.
 *
 * @param persistenceUnitName the persistence unit name the
 *                            synthetic bean is qualified with
 * @param entityManager       the raw {@code EntityManager} the
 *                            factory just produced
 */
public record EntityManagerCreatedEvent(
        String persistenceUnitName,
        EntityManager entityManager) {
}
