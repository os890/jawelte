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
package org.os890.jawelte.module.jta.impl.adapter.tx;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

import javax.sql.DataSource;
import javax.sql.XADataSource;

import jakarta.annotation.Priority;

import org.os890.jawelte.core.api.port.ConfigResolver;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.jpa.api.port.PersistencePropertyResolver;
import org.os890.jawelte.module.jta.api.port.TransactionManagerProvider;
import org.os890.jawelte.module.jta.impl.adapter.jpa.StandaloneJtaPlatform;
import org.os890.jawelte.module.jta.impl.adapter.xa.XaDataSourceWrapper;

/**
 * The active {@link PersistencePropertyResolver} shipped by jta-module:
 * contributes the JTA-mode property pack on top of jpa-module's H2
 * baseline so each {@code EntityManagerFactory} bootstraps in JTA mode.
 *
 * <p>Property contributions:
 * <ul>
 *   <li>{@code jakarta.persistence.transactionType=JTA} — the
 *       Jakarta-Persistence-level switch that flips the EMF from
 *       RESOURCE_LOCAL to JTA. The property name follows the
 *       Jakarta Persistence 3.2 §3.7.1 spec form (camelCase) — note
 *       that the {@code persistence.xml} attribute on the
 *       {@code <persistence-unit>} element uses the kebab-case
 *       {@code transaction-type} variant; only the property-bag
 *       form is camelCase.</li>
 *   <li>{@code hibernate.transaction.coordinator_class=jta} — tells
 *       Hibernate's transaction coordinator factory to use the JTA
 *       coordinator (which drives JDBC connection enlistment off the
 *       JTA outcome via {@code Synchronization}).</li>
 *   <li>{@code hibernate.transaction.jta.platform=...StandaloneJtaPlatform}
 *       — the FQCN of the {@link StandaloneJtaPlatform} that resolves
 *       the {@code TransactionManager} / {@code UserTransaction} via
 *       the active {@code TransactionStrategy}.</li>
 *   <li>{@code jakarta.persistence.jtaDataSource} — set to a per-PU
 *       {@code XaDataSourceWrapper} fronting the configured
 *       {@link XADataSource} (see "MicroProfile Config" below).</li>
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
 *
 * <h2>MicroProfile Config</h2>
 *
 * <p>{@code org.os890.jawelte.module.jta.xa-data-source-class} —
 * the full class name of the {@link XADataSource} implementation
 * the resolver instantiates per persistence unit. The default value
 * ({@code org.h2.jdbcx.JdbcDataSource}) ships in
 * {@code jta-module/impl}'s own
 * {@code META-INF/microprofile-config.properties} at the standard
 * ordinal 100. Consumers running against another database
 * <strong>override</strong> by shipping their own
 * {@code microprofile-config.properties} with {@code config_ordinal}
 * set higher than 100, by passing the key as a system property
 * (ordinal 400), or by setting an environment variable (ordinal 300).
 * The class must be a JavaBean-style {@code XADataSource} with a
 * public no-arg constructor and the standard {@code setURL(String)} /
 * {@code setUser(String)} / {@code setPassword(String)} setters —
 * the JDBC convention every major vendor follows (PostgreSQL's
 * {@code PGXADataSource}, MySQL's {@code MysqlXADataSource}, H2's
 * {@code JdbcDataSource}, …). If the configured class is not on the
 * classpath at runtime, or the key is unset / empty, the resolver
 * falls through to "no jtaDataSource" — Hibernate then uses the
 * JDBC-URL coordinates directly. Vendors whose XA data source needs
 * a different configuration shape ship their own
 * {@code PersistencePropertyResolver} impl.
 */
@Priority(Integer.MAX_VALUE - 1)
public class JtaPersistencePropertyResolver implements PersistencePropertyResolver {

    private static final Logger LOG =
            System.getLogger(JtaPersistencePropertyResolver.class.getName());

    /**
     * MicroProfile Config key for the full class name of the
     * {@link XADataSource} implementation the resolver instantiates.
     * The default value ships in
     * {@code jta-module/impl/src/main/resources/META-INF/microprofile-config.properties}
     * at the standard ordinal 100 — there is no Java-side default,
     * so removing the file or setting the key to an empty string is
     * a deliberate opt-out.
     */
    static final String XA_DATA_SOURCE_CLASS_KEY =
            "org.os890.jawelte.module.jta.xa-data-source-class";

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
        // When no caller-supplied transactionType reaches us, log INFO
        // that we're applying JTA. The resolver doesn't see the
        // persistence.xml-declared transaction-type attribute (Hibernate
        // reads that from the XML itself); existingProperties carries
        // only the property bag jpa-module has already assembled (H2
        // base + MP Config). Absence of the key here therefore means
        // "no MP-Config-supplied value", and a downstream persistence.xml
        // setting of RESOURCE_LOCAL gets auto-corrected to JTA when this
        // bag merges on top.
        if (!existingProperties.containsKey("jakarta.persistence.transactionType")) {
            LOG.log(Level.INFO,
                    () -> "Applying JTA as jakarta.persistence.transactionType for persistence unit '"
                            + persistenceUnitName + "' — no explicit transactionType was configured");
        }
        properties.put("jakarta.persistence.transactionType", "JTA");
        // hibernate.transaction.coordinator_class=jta is optional —
        // Hibernate auto-detects the coordinator from the configured
        // JtaPlatform below. Set explicitly here for an unambiguous
        // bootstrap log and to make the intent visible to anyone
        // dumping the EMF property bag.
        properties.put("hibernate.transaction.coordinator_class", "jta");
        properties.put("hibernate.transaction.jta.platform", StandaloneJtaPlatform.class.getName());
        // DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION is the
        // JPA-recommended mode for JTA: the connection is acquired on
        // first JDBC use and released back to the pool when the JTA
        // tx completes. XaDataSourceWrapper caches the underlying
        // XAConnection per JTA Transaction (keyed by Transaction
        // object), so Hibernate's repeated borrow + return calls
        // within one tx all hit the same enlisted XAResource —
        // RELEASE_AFTER_TRANSACTION cleanly releases the handle on
        // the right boundary, and the wrapper's Synchronization
        // closes the cached XAConnection on tx completion.
        properties.put("hibernate.connection.handling_mode",
                "DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION");
        // Set jakarta.persistence.jtaDataSource to a wrapping of the
        // configured XADataSource (see XA_DATA_SOURCE_CLASS_KEY).
        // First ask the active TransactionManagerProvider for its
        // own pooled DataSource — Atomikos returns an
        // AtomikosDataSourceBean that owns the XA pool and the
        // enlistResource call internally, which is the only way to
        // satisfy its strict resource-recovery model against H2.
        // When the provider has no pooled DataSource of its own
        // (Geronimo, Narayana) the project default
        // XaDataSourceWrapper handles enlistment.
        XADataSource xaDataSource = buildXaDataSourceOrNull(existingProperties);
        if (xaDataSource != null) {
            DataSource jtaDataSource = TestContext
                    .loadService(TransactionManagerProvider.class)
                    .pooledJtaDataSource(xaDataSource, persistenceUnitName)
                    .orElseGet(() -> new XaDataSourceWrapper(xaDataSource, persistenceUnitName));
            properties.put("jakarta.persistence.jtaDataSource", jtaDataSource);
        }
        return properties;
    }

    /**
     * Reflectively build the configured {@link XADataSource} from
     * the {@code jakarta.persistence.jdbc.url} / user / password
     * the H2 branch of {@code JpaCdiExtension} accumulates. The
     * concrete data-source class is read from MP Config under
     * {@link #XA_DATA_SOURCE_CLASS_KEY}. Returns {@code null} when
     * either the JDBC URL is missing, the configured class is unset
     * / empty, or the class is not on the runtime classpath.
     */
    private static XADataSource buildXaDataSourceOrNull(Map<String, Object> existingProperties) {
        Object url = existingProperties.get("jakarta.persistence.jdbc.url");
        if (!(url instanceof String urlString) || urlString.isEmpty()) {
            return null;
        }
        String configuredClassName = configuredXaDataSourceClassNameOrNull();
        if (configuredClassName == null) {
            return null;
        }
        Object user = existingProperties.get("jakarta.persistence.jdbc.user");
        Object password = existingProperties.get("jakarta.persistence.jdbc.password");
        try {
            Class<?> dataSourceClass = Class.forName(
                    configuredClassName, true, Thread.currentThread().getContextClassLoader());
            Object instance = dataSourceClass.getDeclaredConstructor().newInstance();
            dataSourceClass.getMethod("setURL", String.class).invoke(instance, urlString);
            if (user instanceof String userString) {
                dataSourceClass.getMethod("setUser", String.class).invoke(instance, userString);
            }
            if (password instanceof String passwordString) {
                dataSourceClass.getMethod("setPassword", String.class)
                        .invoke(instance, passwordString);
            }
            return (XADataSource) instance;
        } catch (ClassNotFoundException notOnClasspath) {
            LOG.log(Level.DEBUG,
                    "Configured XADataSource class '" + configuredClassName
                            + "' not on the classpath — falling back to non-XA jtaDataSource");
            return null;
        } catch (ReflectiveOperationException reflectionFailure) {
            throw new IllegalStateException(
                    "Failed to construct XADataSource '" + configuredClassName
                            + "' via reflection from existing JDBC properties",
                    reflectionFailure);
        }
    }

    /**
     * Read {@link #XA_DATA_SOURCE_CLASS_KEY} from the active
     * {@code ConfigResolver} (which layers MP Config sources by
     * ordinal). The default value lives in jta-module's own
     * {@code META-INF/microprofile-config.properties} at ordinal
     * 100, so a missing key here means the user deliberately
     * removed it — log it and let the caller fall through. Empty
     * string is also treated as opt-out.
     */
    private static String configuredXaDataSourceClassNameOrNull() {
        try {
            ConfigResolver resolver = TestContext.loadService(ConfigResolver.class);
            if (resolver == null) {
                LOG.log(Level.DEBUG, "ConfigResolver unavailable — no jtaDataSource will be set");
                return null;
            }
            // Trim before the empty-check: a user writing the value
            // in their microprofile-config.properties may include
            // accidental leading/trailing whitespace; Class.forName
            // would fail on " foo.bar.X " with ClassNotFoundException.
            Optional<String> configured = resolver.resolve(XA_DATA_SOURCE_CLASS_KEY)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty());
            if (configured.isEmpty()) {
                LOG.log(Level.DEBUG,
                        XA_DATA_SOURCE_CLASS_KEY
                                + " is unset or empty — no jtaDataSource will be set");
                return null;
            }
            return configured.get();
        } catch (RuntimeException configFailure) {
            LOG.log(Level.DEBUG,
                    "ConfigResolver lookup failed while reading "
                            + XA_DATA_SOURCE_CLASS_KEY + " — no jtaDataSource will be set",
                    configFailure);
            return null;
        }
    }
}
