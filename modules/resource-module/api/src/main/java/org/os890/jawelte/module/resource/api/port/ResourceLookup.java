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
package org.os890.jawelte.module.resource.api.port;

/**
 * Turns the name on a {@code @Resource} declaration into the object to
 * inject.
 *
 * <p>The port says nothing about JNDI on purpose. The shipped adapter
 * resolves against the naming tree jndi-module installs — which is
 * where datasource-module binds its {@code @DataSourceDefinition}
 * entries, so {@code @Resource(lookup = "java:app/jdbc/AppDS")} and
 * {@code @Inject @Named("java:app/jdbc/AppDS") DataSource} reach the
 * same object — but a consumer resolving names from a registry, a map
 * or a container of its own replaces it without this interface
 * changing.
 *
 * <p>Discovered via {@code ServiceLoader} and selected by
 * {@link org.os890.jawelte.core.api.port.TestContext#loadService(Class)},
 * which routes the priority sort through the active
 * {@link org.os890.jawelte.core.api.port.ServicePriorityResolver}.
 * Lower {@code @Priority} value wins, so an application replaces the
 * shipped adapter by shipping its own with a lower number.
 *
 * <p>Implementations must work while the CDI container is being built:
 * the extension resolves the active implementation during
 * {@code BeforeBeanDiscovery} and calls it later, when each bean's
 * fields are injected.
 */
public interface ResourceLookup {

    /**
     * Resolve one name.
     *
     * @param name       the name from the {@code @Resource} declaration
     *                   — its {@code lookup}, {@code mappedName} or
     *                   {@code name}, in that order of preference,
     *                   already trimmed and never blank
     * @param targetType the declared type of the field being injected,
     *                   for implementations that resolve by type as
     *                   well as by name; implementations must not
     *                   reject a resolved object on type grounds, the
     *                   caller reports the mismatch with the field in
     *                   the message
     * @return the bound object, or {@code null} when this
     *         implementation does not know the name — the caller turns
     *         that into a failure naming the field and the name, so an
     *         implementation returning {@code null} is passing the
     *         decision on rather than choosing silence
     * @throws IllegalStateException when the lookup itself cannot be
     *         performed — no naming provider installed, a broken
     *         registry — as opposed to the name simply not being bound
     */
    Object lookup(String name, Class<?> targetType);
}
