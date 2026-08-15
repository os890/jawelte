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
package org.os890.jawelte.module.cdi.api.port;

import java.lang.reflect.Type;
import java.util.Set;

import org.os890.jawelte.core.api.port.TestContext;

/**
 * Declares the types an extension is going to supply with a synthetic
 * bean registered through {@code AfterBeanDiscovery.addBean()}, so
 * cdi-module's auto-mocking leaves them alone.
 *
 * <p><b>Why a declaration is needed at all.</b> Auto-mock collects
 * candidate injection points at {@code ProcessInjectionPoint} and then
 * re-checks each one at {@code AfterBeanDiscovery} with
 * {@code BeanManager.getBeans(...)}, registering a mock only for what is
 * still unsatisfied. That re-check cannot see synthetic beans:
 * {@code addBean()} registrations are not visible from
 * {@code getBeans(...)} during {@code AfterBeanDiscovery}, and
 * {@code ProcessSyntheticBean} is not fired until every
 * {@code AfterBeanDiscovery} observer has run — measured on both
 * OpenWebBeans and Weld. Observer {@code @Priority} does not change
 * either fact, so there is no order in which the re-check would notice
 * them. Left alone, auto-mock registers a second bean of the same type
 * and qualifiers and the deployment fails with
 * {@code AmbiguousResolutionException}.
 *
 * <p>A type therefore has to be <em>declared</em> rather than detected.
 * cdi-module already does this for its own synthetic beans, by
 * consulting the {@code @TestBean} scan result instead of the bean
 * manager; this port is the same idea opened up to the other modules
 * and to user extensions.
 *
 * <p><b>Declare only what is actually going to be registered.</b>
 * {@link #declaredTypes(TestContext)} is called during
 * {@code AfterBeanDiscovery}, late enough that an implementation knows
 * what it found: datasource-module declares {@code javax.sql.DataSource}
 * only when a {@code @DataSourceDefinition} was discovered, and nothing
 * at all otherwise, so a deployment that never declares a data source
 * keeps auto-mocking {@code DataSource} exactly as before. Declaring
 * unconditionally would turn a missing bean into an unsatisfied
 * dependency for everyone who merely has the module on the classpath.
 *
 * <p><b>All providers are consulted</b>, not just the highest-priority
 * one: every module registering synthetic beans has its own types to
 * declare and the results are unioned. Discovery is plain
 * {@code ServiceLoader}, so a provider joins by shipping a
 * {@code META-INF/services/org.os890.jawelte.module.cdi.api.port.SyntheticBeanTypeDeclaration}
 * entry. {@code @Priority} is not consulted — a union has no order.
 *
 * <p>Users who want to suppress auto-mocking for application types
 * without writing an extension have the simpler
 * {@link ExcludedPackageFilter} route instead, driven by the MP Config
 * key {@code org.os890.jawelte.module.cdi.auto-mock.exclude-packages}.
 *
 * <p>Implementations must work while the CDI container is still being
 * built; this port is consulted during {@code AfterBeanDiscovery}, so
 * {@code CDI.current()} is not usable. The active {@link TestContext} is
 * the way in instead: an extension binds what it discovered under
 * {@link TestContext#bindMetadata(Class, Object)} during
 * {@code BeforeBeanDiscovery}, and the provider reads it back with
 * {@link TestContext#getMetadata(Class)} — the same channel cdi-module
 * uses for its own {@code @TestBean} scan result. Keeping the CDI SPI
 * out of the signature is deliberate: this module's api artifact
 * carries ports only, and depends on nothing but core/api.
 */
public interface SyntheticBeanTypeDeclaration {

    /**
     * The types this provider is going to register synthetic beans for
     * in the container currently being built.
     *
     * <p>Matching against an injection point is by equality against
     * either the injection point's target type or its raw class, so
     * both {@code DataSource.class} and a parameterized type such as
     * {@code new TypeLiteral<Repository<Order>>() {}.getType()} work.
     *
     * @param testContext the active test context, carrying whatever the
     *                    declaring extension bound during
     *                    {@code BeforeBeanDiscovery}
     * @return the declared types; empty when this provider is going to
     *         register nothing, which must never be {@code null}
     */
    Set<Type> declaredTypes(TestContext testContext);
}
