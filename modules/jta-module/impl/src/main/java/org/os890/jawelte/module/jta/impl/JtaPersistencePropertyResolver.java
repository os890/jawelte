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

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;

import javax.sql.XADataSource;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.jpa.api.port.PersistencePropertyResolver;
import org.os890.jawelte.module.jta.impl.hibernate.StandaloneJtaPlatform;
import org.os890.jawelte.module.jta.impl.xa.XaDataSourceWrapper;

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

    private static final Logger LOG =
            System.getLogger(JtaPersistencePropertyResolver.class.getName());

    private static final String H2_XA_DATA_SOURCE_CLASS = "org.h2.jdbcx.JdbcDataSource";

    /** No-arg constructor required by {@link ServiceLoader}. */
    public JtaPersistencePropertyResolver() {
    }

    @Override
    public Map<String, Object> resolvePropertiesFor(String persistenceUnitName) {
        return resolvePropertiesFor(persistenceUnitName, Map.of());
    }

    @Override
    public Map<String, Object> resolvePropertiesFor(
            String persistenceUnitName, Map<String, Object> existingProperties) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("jakarta.persistence.transaction-type", "JTA");
        properties.put("hibernate.transaction.coordinator_class", "jta");
        properties.put("hibernate.transaction.jta.platform", StandaloneJtaPlatform.class.getName());
        // jtaDataSource is opt-in via the boolean property below: a
        // bare JTA coordinator (without an XA-enlisting DataSource)
        // is enough for single-PU scenarios — Hibernate's JTA
        // coordinator drives JDBC commit via a Synchronization on
        // afterCompletion and the connection's auto-commit /
        // begin / commit is managed inside that callback. Multi-PU
        // XA (Test Scenario 10/11/12) flips the flag on so the
        // XaDataSourceWrapper provides per-PU XA enlistment.
        if (Boolean.parseBoolean(System.getProperty("jawelte.jta.useXaDataSource", "false"))) {
            XADataSource xaDataSource = buildH2XaDataSourceOrNull(existingProperties);
            if (xaDataSource != null) {
                properties.put("jakarta.persistence.jtaDataSource",
                        new XaDataSourceWrapper(xaDataSource, persistenceUnitName));
            }
        }
        return properties;
    }

    /**
     * Reflectively build an H2 {@link XADataSource} from the
     * {@code jakarta.persistence.jdbc.url} / user / password the H2
     * branch of {@code JpaCdiExtension} accumulates. Returns
     * {@code null} when H2's {@code JdbcDataSource} is not on the
     * classpath (production consumers ship their own
     * {@link PersistencePropertyResolver} that builds whatever
     * {@code XADataSource} their database vendor provides).
     */
    private static XADataSource buildH2XaDataSourceOrNull(Map<String, Object> existingProperties) {
        Object url = existingProperties.get("jakarta.persistence.jdbc.url");
        if (!(url instanceof String urlString) || urlString.isEmpty()) {
            return null;
        }
        Object user = existingProperties.get("jakarta.persistence.jdbc.user");
        Object password = existingProperties.get("jakarta.persistence.jdbc.password");
        try {
            Class<?> jdbcDataSourceClass = Class.forName(
                    H2_XA_DATA_SOURCE_CLASS, true, Thread.currentThread().getContextClassLoader());
            Object instance = jdbcDataSourceClass.getDeclaredConstructor().newInstance();
            jdbcDataSourceClass.getMethod("setURL", String.class).invoke(instance, urlString);
            if (user instanceof String userString) {
                jdbcDataSourceClass.getMethod("setUser", String.class).invoke(instance, userString);
            }
            if (password instanceof String passwordString) {
                jdbcDataSourceClass.getMethod("setPassword", String.class)
                        .invoke(instance, passwordString);
            }
            return (XADataSource) instance;
        } catch (ClassNotFoundException h2Absent) {
            LOG.log(Level.DEBUG,
                    "H2 JdbcDataSource not on the classpath — falling back to non-XA jtaDataSource");
            return null;
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to construct H2 XADataSource via reflection from existing JDBC properties",
                    reflectionFailure);
        }
    }
}
