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
package org.os890.jawelte.module.datasource.api.port;

import javax.sql.DataSource;

import jakarta.annotation.sql.DataSourceDefinition;

/**
 * Port that turns a {@link DataSourceDefinition} into a live
 * {@link DataSource}.
 *
 * <p>The annotation is a declaration, not an implementation: it names
 * a vendor class and carries the attributes to configure it with.
 * Everything about how those attributes reach the vendor object —
 * setter conventions, which attributes a given vendor honours, whether
 * a pool is involved — is vendor-specific, so it lives behind this
 * port rather than in the discovery or lifecycle code.
 *
 * <p>datasource-module/impl ships a reflective implementation at
 * {@code @Priority(Integer.MAX_VALUE)}, so a consumer can register a
 * factory that builds a pooled {@code DataSource} (HikariCP, Agroal,
 * a container's own) at a lower priority via
 * {@code META-INF/services} and have every {@code @DataSourceDefinition}
 * in the suite routed through it, without touching a single test.
 *
 * <p>Resolved via {@code TestContext.loadService(DataSourceFactory.class)}.
 */
public interface DataSourceFactory {

    /**
     * Build the {@link DataSource} a definition describes.
     *
     * <p>Called once per definition per test class, during the
     * lifecycle adapter's {@code beforeAll}. The returned instance is
     * bound into JNDI under {@link DataSourceDefinition#name()} and
     * registered as an injectable bean.
     *
     * @param definition the declared definition; never {@code null}
     * @return the configured data source; never {@code null}
     * @throws IllegalStateException when the definition cannot be
     *         realised — an unknown {@code className}, a class that is
     *         not a data source type, or an attribute the vendor class
     *         rejects
     */
    DataSource create(DataSourceDefinition definition);
}
