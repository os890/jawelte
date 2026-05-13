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

/**
 * Auto-mocking exclude policy. cdi-module's CDI Extension consults
 * the active {@code ExcludedPackageFilter} at two points:
 *
 * <ul>
 *   <li>{@code ProcessInjectionPoint} — drops IPs whose owning bean
 *       is framework-internal (Weld / OWB / DeltaSpike / SmallRye)
 *       before they enter the auto-mock candidate set, via
 *       {@link #isOwningBeanExcluded(Class)}. Stops the loop from
 *       trying to synthesise a Mockito mock for IPs the CDI runtime
 *       satisfies internally (e.g. Weld-SE's {@code RunnableDecorator}
 *       injecting {@code Runnable}).</li>
 *   <li>{@code AfterBeanDiscovery} — for IPs that survived the
 *       collection-time filter, a final check on the IP's target
 *       type via {@link #isExcluded(Class)} skips synthetic-mock
 *       registration for the type.</li>
 * </ul>
 *
 * <p>Discovered via {@code ServiceLoader} and selected by
 * {@link org.os890.jawelte.core.api.port.TestContext#loadService(Class)},
 * which routes the priority sort through the active
 * {@link org.os890.jawelte.core.api.port.ServicePriorityResolver}.
 * Lower {@code @Priority} value wins.
 *
 * <p>The default implementation lives in {@code cdi-module/impl}
 * ({@code DefaultExcludedPackageFilter}) and reads a comma-separated
 * package-prefix list from the MicroProfile Config key
 * {@code org.os890.jawelte.module.cdi.auto-mock.exclude-packages}
 * (with the standard dot-then-underscore fallback). It excludes a
 * type when any class in the type's supertype hierarchy lives under
 * one of the configured prefixes. The default also carries a
 * built-in framework-internal prefix list applied to
 * {@link #isOwningBeanExcluded(Class)} so common CDI-runtime
 * infrastructure (Weld, OWB, DeltaSpike, SmallRye) is always
 * filtered without any user configuration. Custom implementations
 * replace the default by providing their own {@code ServiceLoader}
 * entry plus a lower-numbered {@code @Priority}.
 *
 * <p>{@code @TestBean}-declared types bypass this filter — explicit
 * user opt-in always wins. The filter only governs <em>implicit</em>
 * auto-mocking decisions.
 *
 * <p>Implementations must work before the CDI container is up; this
 * port is consulted during {@code BeforeBeanDiscovery} /
 * {@code ProcessInjectionPoint} / {@code AfterBeanDiscovery}.
 */
public interface ExcludedPackageFilter {

    /**
     * Whether the given type should be skipped by auto-mocking.
     *
     * @param rawType the unsatisfied injection-point raw type the CDI
     *                Extension is considering for synthetic-mock
     *                registration
     * @return {@code true} to skip auto-mocking for this type;
     *         {@code false} to proceed
     */
    boolean isExcluded(Class<?> rawType);

    /**
     * Whether IPs declared by the given owning bean class should be
     * dropped at {@code ProcessInjectionPoint} time, before they
     * enter the auto-mock candidate set. Lets the filter veto
     * framework-internal IPs (e.g. Weld-SE's
     * {@code RunnableDecorator} injecting {@code Runnable},
     * SmallRye Config's producers injecting
     * {@code jakarta.enterprise.inject.spi.InjectionPoint}) so the
     * extension never reaches {@code mockFactory.create(...)} for
     * those types.
     *
     * <p>Default returns {@code false}, preserving the behaviour of
     * any custom implementation that predates this method.
     *
     * @param owningBeanClass the bean class declaring the IP under
     *                        consideration
     * @return {@code true} to drop the IP from the auto-mock
     *         candidate set; {@code false} to allow further
     *         processing
     */
    default boolean isOwningBeanExcluded(Class<?> owningBeanClass) {
        return false;
    }
}
