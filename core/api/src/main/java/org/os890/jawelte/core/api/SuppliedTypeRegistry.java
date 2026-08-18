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
package org.os890.jawelte.core.api;

import java.lang.reflect.Type;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.os890.jawelte.core.api.port.TestContext;

/**
 * The types a module has supplied to the container being built, kept on
 * the active {@link TestContext} so every part of the framework can both
 * add to it and read it.
 *
 * <p>A module that registers a bean the container could not have
 * discovered — datasource-module turning a
 * {@code @DataSourceDefinition} into a {@code DataSource} bean, say —
 * says so here, in the same breath as registering it:
 *
 * <pre>{@code
 * SuppliedTypeRegistry.of(testContext).markSupplied(DataSource.class);
 * event.addBean().types(DataSource.class)....;
 * }</pre>
 *
 * <p><b>Why anything has to be said at all.</b> The container will not
 * answer the question in time. Beans added through
 * {@code AfterBeanDiscovery.addBean()} are invisible to
 * {@code BeanManager.getBeans(...)} for the whole of
 * {@code AfterBeanDiscovery}, and {@code ProcessSyntheticBean} is not
 * fired until every {@code AfterBeanDiscovery} observer has run —
 * measured on both OpenWebBeans and Weld, at any observer priority. So
 * there is no moment at which one extension can ask the container what
 * another has just registered <em>and</em> still act on the answer.
 *
 * <p>Its reader today is cdi-module's auto-mocking, which stands in for
 * injection points nothing satisfies. Without this it would stand in for
 * a type a module has just supplied, giving two beans of the same type
 * and qualifiers and a deployment that fails with
 * {@code AmbiguousResolutionException}.
 *
 * <p><b>Ordering is the contract.</b> A supplier must mark the type
 * before the reader looks, which for CDI lifecycle observers means
 * {@code @Priority} on the observer method: suppliers observe
 * {@code AfterBeanDiscovery} early, cdi-module's auto-mock observes it
 * late.
 *
 * <p>Held per {@link TestContext}, so it starts empty for every test
 * class and needs no clearing.
 */
public class SuppliedTypeRegistry {

    private final Set<Type> supplied = ConcurrentHashMap.newKeySet();

    /** Created only through {@link #of(TestContext)}. */
    protected SuppliedTypeRegistry() {
    }

    /**
     * The registry for this test context, created and bound on first
     * use.
     *
     * @param testContext the active test context
     * @return the registry; never {@code null}
     */
    public static SuppliedTypeRegistry of(TestContext testContext) {
        return testContext.getMetadata(SuppliedTypeRegistry.class)
                .orElseGet(() -> {
                    SuppliedTypeRegistry registry = new SuppliedTypeRegistry();
                    testContext.bindMetadata(SuppliedTypeRegistry.class, registry);
                    return registry;
                });
    }

    /**
     * Record that this module supplies beans of the given type.
     *
     * @param type the bean type being supplied
     */
    public void markSupplied(Type type) {
        supplied.add(type);
    }

    /**
     * Whether anything has been supplied for the given type.
     *
     * <p>Both the injection point's target type and its raw class are
     * worth asking about, so a supplier marking {@code DataSource.class}
     * covers a plain injection point and a marker of a parameterized
     * type covers that.
     *
     * @param type the type an injection point asks for
     * @return {@code true} when a module has supplied it
     */
    public boolean isSupplied(Type type) {
        return supplied.contains(type);
    }

    /**
     * Whether anything has been supplied at all.
     *
     * @return {@code true} when at least one type was marked
     */
    public boolean isEmpty() {
        return supplied.isEmpty();
    }
}
