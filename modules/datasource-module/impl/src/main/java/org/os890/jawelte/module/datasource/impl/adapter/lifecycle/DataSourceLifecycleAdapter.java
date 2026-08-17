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
package org.os890.jawelte.module.datasource.impl.adapter.lifecycle;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import jakarta.annotation.Priority;
import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.datasource.api.port.DataSourceFactory;
import org.os890.jawelte.module.datasource.impl.DataSourceRegistry;
import org.os890.jawelte.module.datasource.impl.adapter.extension.DataSourceDefinitionCdiExtension;
import org.os890.jawelte.module.datasource.impl.adapter.jndi.DataSourceJndiBinder;

/**
 * {@link TestModuleLifecyclePort} adapter shipped by
 * datasource-module/impl. Builds one {@link DataSource} per discovered
 * {@code @DataSourceDefinition} in {@code beforeAll}, binds each into
 * JNDI, and closes and unbinds them in {@code afterAll}.
 *
 * <p><b>Priority.</b> {@code @Priority(150)} — after scope-module
 * (100), so the test scopes are live, and before jpa-module (200), so
 * a declared data source already exists by the time the JPA adapter
 * boots. That ordering is what a later "persistence unit resolves its
 * declared {@code <jta-data-source>}" change needs, and choosing it
 * now costs nothing.
 *
 * <p><b>No-op unless something was declared.</b> The adapter reads the
 * discovery map off {@link DataSourceDefinitionCdiExtension}; an empty
 * map means an immediate return, with no registry access, no naming
 * lookup and no CDI resolution. A test class without
 * {@code @DataSourceDefinition} — and therefore every existing test in
 * the suite — sees exactly the behaviour it saw before this module
 * existed, even with the module on the classpath.
 *
 * <p><b>Build failure recovery.</b> If building the second of three
 * data sources throws, the first one is closed and unbound before the
 * failure propagates. The framework does not call {@code afterAll} for
 * an adapter whose {@code beforeAll} threw, so this self-cleanup is
 * the only thing that releases what was already opened.
 *
 * <p><b>Closing.</b> {@code javax.sql.DataSource} has no
 * {@code close()} — a vendor either implements {@link AutoCloseable}
 * or exposes its own no-arg {@code close()}, and many (H2's
 * {@code JdbcDataSource} among them) hold nothing that needs closing
 * at all. All three cases are handled and the third is not an error.
 */
@Priority(150)
public class DataSourceLifecycleAdapter implements TestModuleLifecyclePort {

    /** No-arg constructor required by SPI {@code ServiceLoader} lookup. */
    public DataSourceLifecycleAdapter() {
    }

    @Override
    public void beforeAll(TestContext testContext) {
        Map<String, DataSourceDefinition> definitions = discoveredDefinitions();
        if (definitions.isEmpty()) {
            return;
        }
        DataSourceFactory factory = resolveFactory();
        DataSourceRegistry registry = CDI.current().select(DataSourceRegistry.class).get();
        List<String> built = new ArrayList<>();
        try {
            for (Map.Entry<String, DataSourceDefinition> entry : definitions.entrySet()) {
                String name = entry.getKey();
                DataSource dataSource = factory.create(entry.getValue());
                if (dataSource == null) {
                    throw new IllegalStateException(
                            factory.getClass().getName() + " returned null for @DataSourceDefinition(name = \""
                                    + name + "\")");
                }
                registry.register(name, dataSource);
                built.add(name);
                DataSourceJndiBinder.bind(name, dataSource);
            }
        } catch (RuntimeException buildFailure) {
            releaseBestEffort(registry, built, buildFailure);
            throw buildFailure;
        }
    }

    @Override
    public void afterAll(TestContext testContext) {
        if (discoveredDefinitions().isEmpty()) {
            return;
        }
        DataSourceRegistry registry = CDI.current().select(DataSourceRegistry.class).get();
        List<Throwable> failures = new ArrayList<>();
        try {
            for (Map.Entry<String, DataSource> entry : registry.entries().entrySet()) {
                try {
                    DataSourceJndiBinder.unbind(entry.getKey());
                    closeIfCloseable(entry.getValue());
                } catch (RuntimeException closeFailure) {
                    failures.add(closeFailure);
                }
            }
        } finally {
            registry.clear();
        }
        if (!failures.isEmpty()) {
            RuntimeException first = new IllegalStateException(
                    "Failed to release " + failures.size() + " declared DataSource(s)", failures.get(0));
            for (int i = 1; i < failures.size(); i++) {
                first.addSuppressed(failures.get(i));
            }
            throw first;
        }
    }

    private static Map<String, DataSourceDefinition> discoveredDefinitions() {
        BeanManager beanManager = CDI.current().getBeanManager();
        DataSourceDefinitionCdiExtension extension =
                beanManager.getExtension(DataSourceDefinitionCdiExtension.class);
        return extension.discoveredDefinitions();
    }

    private static DataSourceFactory resolveFactory() {
        DataSourceFactory factory = TestContext.loadService(DataSourceFactory.class);
        if (factory == null) {
            throw new IllegalStateException(
                    "No " + DataSourceFactory.class.getName() + " on the classpath. "
                            + "jawelte-datasource-module-impl ships the default one; a consumer replacing it "
                            + "must keep exactly one registered via META-INF/services.");
        }
        return factory;
    }

    private static void releaseBestEffort(DataSourceRegistry registry, List<String> built, RuntimeException primary) {
        for (String name : built) {
            try {
                DataSourceJndiBinder.unbind(name);
                closeIfCloseable(registry.get(name));
            } catch (RuntimeException cleanupFailure) {
                primary.addSuppressed(cleanupFailure);
            }
        }
        registry.clear();
    }

    /**
     * Close a data source that can be closed.
     *
     * <p>Three shapes, in order: {@link AutoCloseable}, a vendor's own
     * public no-arg {@code close()}, or nothing to close — the last is
     * the common case (H2's {@code JdbcDataSource} holds no resource)
     * and is not an error.
     */
    private static void closeIfCloseable(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception closeFailure) {
                throw new IllegalStateException(
                        "Failed to close " + dataSource.getClass().getName(), closeFailure);
            }
            return;
        }
        Method close;
        try {
            close = dataSource.getClass().getMethod("close");
        } catch (NoSuchMethodException nothingToClose) {
            return;
        }
        try {
            close.invoke(dataSource);
        } catch (ReflectiveOperationException closeFailure) {
            throw new IllegalStateException(
                    "Failed to close " + dataSource.getClass().getName(), closeFailure);
        }
    }
}
