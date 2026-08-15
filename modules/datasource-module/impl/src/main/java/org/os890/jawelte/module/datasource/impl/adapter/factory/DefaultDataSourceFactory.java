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
package org.os890.jawelte.module.datasource.impl.adapter.factory;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.XADataSource;

import jakarta.annotation.Priority;
import jakarta.annotation.sql.DataSourceDefinition;

import org.eclipse.microprofile.config.ConfigProvider;
import org.os890.jawelte.module.datasource.api.port.DataSourceFactory;
import org.os890.jawelte.module.datasource.impl.util.DataSourceAdapters;

/**
 * Default {@link DataSourceFactory} shipped by datasource-module/impl:
 * instantiates the class named by
 * {@link DataSourceDefinition#className()} and configures it through
 * its JavaBean setters.
 *
 * <p><b>Why reflection.</b> {@code @DataSourceDefinition} names a
 * vendor class and nothing in the platform describes how to configure
 * it — there is no common configuration interface for data sources.
 * Every driver exposes the same attributes as ordinary setters
 * ({@code setUrl}, {@code setUser}, {@code setPassword}, ...), so
 * that is the contract used here. It also keeps this module free of
 * any JDBC-driver dependency: the vendor class is loaded from the
 * test's own classpath.
 *
 * <p><b>Attribute naming.</b> Drivers disagree on capitalisation for
 * URL in particular — H2 exposes {@code setURL} and {@code setUrl},
 * PostgreSQL only {@code setUrl}, some Oracle versions only
 * {@code setURL}. Each attribute is therefore tried against a list of
 * candidate setter names and the first one the class actually declares
 * wins.
 *
 * <p><b>Attributes honoured</b>: {@code url}, {@code user},
 * {@code password}, {@code databaseName}, {@code serverName},
 * {@code portNumber}, {@code loginTimeout}, {@code description}, and
 * every entry of {@code properties()} (parsed as {@code name=value}
 * and applied as a setter of that name).
 *
 * <p><b>Attributes deliberately ignored</b>, because they describe a
 * connection pool this factory does not create — {@code initialPoolSize},
 * {@code minPoolSize}, {@code maxPoolSize}, {@code maxIdleTime},
 * {@code maxStatements} — and {@code transactional} and
 * {@code isolationLevel}, which describe transactional behaviour that
 * belongs to the jta-module follow-up. An attribute is only silently
 * skipped when the vendor class has no matching setter; nothing here
 * pretends to apply them. Register a lower-priority
 * {@code DataSourceFactory} to honour them.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} — the lowest-priority
 * fallback, so any consumer-supplied factory wins.
 */
@Priority(Integer.MAX_VALUE)
public class DefaultDataSourceFactory implements DataSourceFactory {

    /**
     * MicroProfile Config key prefix for redirecting a declared data
     * source. The definition's own name completes it — e.g.
     * {@code org.os890.jawelte.module.datasource."java:app/jdbc/AppDS".url}.
     */
    public static final String URL_OVERRIDE_PREFIX = "org.os890.jawelte.module.datasource.";

    /** Suffix of the url-override key. */
    public static final String URL_OVERRIDE_SUFFIX = ".url";

    /**
     * Attribute to candidate setter names, in the order they are
     * tried. The first setter the vendor class declares is used.
     */
    private static final Map<String, String[]> SETTER_CANDIDATES = new LinkedHashMap<>();

    static {
        SETTER_CANDIDATES.put("url", new String[] {"setURL", "setUrl"});
        SETTER_CANDIDATES.put("user", new String[] {"setUser", "setUsername", "setUserName"});
        SETTER_CANDIDATES.put("password", new String[] {"setPassword"});
        SETTER_CANDIDATES.put("databaseName", new String[] {"setDatabaseName"});
        SETTER_CANDIDATES.put("serverName", new String[] {"setServerName"});
        SETTER_CANDIDATES.put("description", new String[] {"setDescription"});
    }

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public DefaultDataSourceFactory() {
    }

    @Override
    public DataSource create(DataSourceDefinition definition) {
        Object vendorInstance = instantiate(definition);
        applyStringAttributes(vendorInstance, definition);
        applyIntAttributes(vendorInstance, definition);
        applyDeclaredProperties(vendorInstance, definition);
        return asDataSource(vendorInstance, definition);
    }

    private static Object instantiate(DataSourceDefinition definition) {
        String className = definition.className();
        if (className == null || className.isBlank()) {
            throw new IllegalStateException(
                    "@DataSourceDefinition(name = \"" + definition.name()
                            + "\") declares no className — there is nothing to instantiate.");
        }
        try {
            ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            Class<?> vendorClass = Class.forName(className, true, tccl);
            return vendorClass.getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException notOnClasspath) {
            throw new IllegalStateException(
                    "@DataSourceDefinition(name = \"" + definition.name() + "\") names className '"
                            + className + "', which is not on the test classpath. Add the JDBC driver "
                            + "to the test's dependencies.",
                    notOnClasspath);
        } catch (ReflectiveOperationException notInstantiable) {
            throw new IllegalStateException(
                    "@DataSourceDefinition(name = \"" + definition.name() + "\") names className '"
                            + className + "', which has no usable public no-arg constructor.",
                    notInstantiable);
        }
    }

    private static void applyStringAttributes(Object target, DataSourceDefinition definition) {
        applyIfSet(target, "url", resolveUrl(definition));
        applyIfSet(target, "user", definition.user());
        applyIfSet(target, "password", definition.password());
        applyIfSet(target, "databaseName", definition.databaseName());
        applyIfSet(target, "serverName", definition.serverName());
        applyIfSet(target, "description", definition.description());
    }

    /**
     * The url to configure the vendor object with: the definition's own,
     * unless a test run redirects it.
     *
     * <p>A production {@code @DataSourceDefinition} names a production
     * database, and a <em>file</em> database is the case that bites —
     * the file is created wherever the test JVM runs, survives the
     * suite, and the next build reopens it with the previous build's
     * rows still in it. That reads as flakiness rather than as leftover
     * state, and it leaves a stray file in the working tree.
     *
     * <p>Redirecting it must not require replacing the factory: that
     * would have every consumer re-implementing a URL rewrite for a
     * vendor syntax a factory port should not have to guess at. The
     * redirect is therefore a MicroProfile Config key carrying the
     * definition's own name, read the same way every other module reads
     * its keys:
     *
     * <pre>
     * org.os890.jawelte.module.datasource."java:app/jdbc/AppDS".url=jdbc:h2:mem:app;DB_CLOSE_DELAY=-1
     * </pre>
     *
     * <p>Absent the key nothing changes and the declaration's own url is
     * used, so this is invisible to anyone who does not need it.
     */
    private static String resolveUrl(DataSourceDefinition definition) {
        String key = URL_OVERRIDE_PREFIX + definition.name() + URL_OVERRIDE_SUFFIX;
        return ConfigProvider.getConfig()
                .getOptionalValue(key, String.class)
                .map(String::trim)
                .filter(override -> !override.isEmpty())
                .orElseGet(definition::url);
    }

    private static void applyIfSet(Object target, String attribute, String value) {
        if (value == null || value.isEmpty()) {
            // The annotation's own default is the empty string, so an
            // empty value means "not declared" rather than "set to
            // empty" — leave the vendor default in place.
            return;
        }
        for (String setterName : SETTER_CANDIDATES.get(attribute)) {
            if (invokeSetter(target, setterName, String.class, value)) {
                return;
            }
        }
    }

    private static void applyIntAttributes(Object target, DataSourceDefinition definition) {
        if (definition.portNumber() >= 0) {
            invokeSetter(target, "setPortNumber", int.class, definition.portNumber());
        }
        if (definition.loginTimeout() > 0) {
            invokeSetter(target, "setLoginTimeout", int.class, definition.loginTimeout());
        }
    }

    /**
     * Apply {@code properties()} entries, each written as
     * {@code name=value}. The name is turned into a setter
     * ({@code fetchSize} becomes {@code setFetchSize}) and applied as
     * a String; if the vendor exposes it as an {@code int} or
     * {@code boolean} instead, the parsed form is tried next.
     */
    private static void applyDeclaredProperties(Object target, DataSourceDefinition definition) {
        for (String property : definition.properties()) {
            int separator = property.indexOf('=');
            if (separator <= 0) {
                throw new IllegalStateException(
                        "@DataSourceDefinition(name = \"" + definition.name()
                                + "\") declares the property '" + property
                                + "', which is not in name=value form.");
            }
            String name = property.substring(0, separator).trim();
            String value = property.substring(separator + 1).trim();
            String setterName = "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
            if (invokeSetter(target, setterName, String.class, value)) {
                continue;
            }
            if (applyParsedProperty(target, setterName, value)) {
                continue;
            }
            throw new IllegalStateException(
                    "@DataSourceDefinition(name = \"" + definition.name() + "\") declares the property '"
                            + name + "', but " + target.getClass().getName() + " has no matching "
                            + setterName + "(String|int|long|boolean) setter.");
        }
    }

    private static boolean applyParsedProperty(Object target, String setterName, String value) {
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            if (invokeSetter(target, setterName, boolean.class, Boolean.parseBoolean(value))) {
                return true;
            }
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed >= Integer.MIN_VALUE && parsed <= Integer.MAX_VALUE
                    && invokeSetter(target, setterName, int.class, (int) parsed)) {
                return true;
            }
            return invokeSetter(target, setterName, long.class, parsed);
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    /**
     * Invoke a setter if the target declares it.
     *
     * @return {@code true} when the setter existed and was invoked,
     *         {@code false} when the class has no such setter — the
     *         caller then tries the next candidate
     */
    private static boolean invokeSetter(Object target, String setterName, Class<?> parameterType, Object value) {
        Method setter;
        try {
            setter = target.getClass().getMethod(setterName, parameterType);
        } catch (NoSuchMethodException noSuchSetter) {
            return false;
        }
        try {
            setter.invoke(target, value);
            return true;
        } catch (ReflectiveOperationException rejected) {
            throw new IllegalStateException(
                    target.getClass().getName() + "." + setterName + " rejected the configured value '"
                            + value + "'",
                    rejected);
        }
    }

    /**
     * Narrow the configured vendor object to a {@link DataSource}.
     *
     * <p>{@code className} is allowed to name a {@code DataSource}, an
     * {@code XADataSource} or a {@code ConnectionPoolDataSource}. Only
     * the first is directly usable; the other two are connection
     * <em>factories</em> and get a thin adapter so that callers see one
     * type regardless of what the test declared.
     */
    private static DataSource asDataSource(Object vendorInstance, DataSourceDefinition definition) {
        if (vendorInstance instanceof DataSource dataSource) {
            return dataSource;
        }
        if (vendorInstance instanceof XADataSource xaDataSource) {
            return DataSourceAdapters.of(xaDataSource);
        }
        if (vendorInstance instanceof ConnectionPoolDataSource pooledDataSource) {
            return DataSourceAdapters.of(pooledDataSource);
        }
        throw new IllegalStateException(
                "@DataSourceDefinition(name = \"" + definition.name() + "\") names className '"
                        + definition.className() + "', which is none of javax.sql.DataSource, "
                        + "javax.sql.XADataSource or javax.sql.ConnectionPoolDataSource.");
    }
}
