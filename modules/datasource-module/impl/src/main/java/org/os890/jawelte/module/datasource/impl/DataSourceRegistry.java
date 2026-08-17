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
package org.os890.jawelte.module.datasource.impl;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.sql.DataSource;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Per-container registry of the {@link DataSource} instances built for
 * the active test class, keyed by the {@code name} their
 * {@code @DataSourceDefinition} declared.
 *
 * <p>Two consumers meet here, which is why the registry exists as a
 * bean rather than as state on either of them. The lifecycle adapter
 * fills it in {@code beforeAll} and clears it in {@code afterAll}; the
 * synthetic beans registered by the CDI extension read from it at
 * injection time, which happens later and on a different call path.
 * Holding the instances here also means an injected {@code DataSource}
 * and a JNDI lookup of the same name hand back the identical object.
 *
 * <p>{@code @ApplicationScoped}, so its lifetime is the CDI container's
 * — one per test class, as jawelte boots a container per test class.
 */
@ApplicationScoped
public class DataSourceRegistry {

    /**
     * The built data sources, keyed by declared name.
     *
     * <p>Concurrent because the write and the reads are on different call
     * paths and not necessarily on the same thread: the lifecycle adapter
     * fills this in {@code beforeAll}, while the synthetic beans read it
     * whenever an injection point is resolved — including from a thread a
     * test spawned itself, which the tree does do (see
     * {@code tests/scope-module} scenario 09). A plain map would leave
     * those reads without a happens-before edge to the writes.
     */
    private final ConcurrentMap<String, DataSource> dataSourcesByName = new ConcurrentHashMap<>();

    /** No-arg constructor required by CDI. */
    public DataSourceRegistry() {
    }

    /**
     * Register a data source under its declared name.
     *
     * @param name       the {@code @DataSourceDefinition} name
     * @param dataSource the built data source
     */
    public void register(String name, DataSource dataSource) {
        dataSourcesByName.put(name, dataSource);
    }

    /**
     * Look up a registered data source.
     *
     * @param name the {@code @DataSourceDefinition} name
     * @return the data source registered under the name
     * @throws IllegalStateException if nothing is registered under it —
     *         which means the definition was discovered (the injection
     *         point resolved) but the lifecycle adapter never ran, so
     *         failing loudly beats handing back {@code null}
     */
    public DataSource get(String name) {
        DataSource dataSource = dataSourcesByName.get(name);
        if (dataSource == null) {
            throw new IllegalStateException(
                    "No DataSource registered under '" + name + "'. Known names: "
                            + new TreeMap<>(dataSourcesByName).keySet()
                            + ". A @DataSourceDefinition was discovered for this name but never built — "
                            + "is jawelte-datasource-module-impl's lifecycle adapter on the classpath?");
        }
        return dataSource;
    }

    /**
     * The registered entries.
     *
     * @return an unmodifiable view of name to data source
     */
    public Map<String, DataSource> entries() {
        return Collections.unmodifiableMap(dataSourcesByName);
    }

    /** Drop every entry; called from the lifecycle adapter's {@code afterAll}. */
    public void clear() {
        dataSourcesByName.clear();
    }
}
