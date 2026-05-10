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
package org.os890.jawelte.module.jta.impl;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.jpa.api.port.PersistencePropertyResolver;
import org.os890.jawelte.module.jta.impl.hibernate.StandaloneJtaPlatform;

/**
 * The active {@link PersistencePropertyResolver} shipped by jta-module:
 * contributes the JTA-mode property pack on top of jpa-module's H2
 * baseline so each {@code EntityManagerFactory} bootstraps in JTA mode.
 *
 * <p>Property contributions:
 * <ul>
 *   <li>{@code jakarta.persistence.transaction-type=JTA} — the
 *       Jakarta-Persistence-level switch that flips the EMF from
 *       RESOURCE_LOCAL to JTA.</li>
 *   <li>{@code hibernate.transaction.coordinator_class=jta} — tells
 *       Hibernate's transaction coordinator factory to use the JTA
 *       coordinator (which drives JDBC connection enlistment off the
 *       JTA outcome via {@code Synchronization}).</li>
 *   <li>{@code hibernate.transaction.jta.platform=...StandaloneJtaPlatform}
 *       — the FQCN of the {@link StandaloneJtaPlatform} that resolves
 *       the {@code TransactionManager} / {@code UserTransaction} via
 *       the active {@code TransactionStrategy}.</li>
 *   <li>{@code jakarta.persistence.jtaDataSource} — set to a per-PU
 *       {@code XaDataSourceWrapper} so multi-PU writes flow through
 *       the JTA two-phase-commit machinery (unset when
 *       {@code XaDataSourceWrapper} is unavailable on the runtime
 *       classpath).</li>
 * </ul>
 *
 * <p>{@code @Priority(Integer.MAX_VALUE - 1)} — wins over any future
 * lower-priority defaults; consumers override by shipping their own
 * resolver at a lower {@code @Priority}.
 *
 * <p>The resolver is generic — it does not depend on any specific JTA
 * implementation. The same property pack works for Geronimo, Narayana,
 * and Atomikos because the {@code TransactionManager} and
 * {@code UserTransaction} indirection lives entirely behind
 * {@link StandaloneJtaPlatform}.
 */
@Priority(Integer.MAX_VALUE - 1)
public class JtaPersistencePropertyResolver implements PersistencePropertyResolver {

    /** No-arg constructor required by {@link ServiceLoader}. */
    public JtaPersistencePropertyResolver() {
    }

    @Override
    public Map<String, Object> resolvePropertiesFor(String persistenceUnitName) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("jakarta.persistence.transaction-type", "JTA");
        properties.put("hibernate.transaction.coordinator_class", "jta");
        properties.put("hibernate.transaction.jta.platform", StandaloneJtaPlatform.class.getName());
        return properties;
    }
}
