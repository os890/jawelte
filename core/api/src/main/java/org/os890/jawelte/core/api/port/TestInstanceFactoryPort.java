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
package org.os890.jawelte.core.api.port;

/**
 * Optional SPI that produces the test instance JUnit will use for an
 * {@code @EnableTestBeans} test class. Discovered via
 * {@code ServiceLoader}; zero or one implementation expected.
 *
 * <p>The core ships a JUnit {@code TestInstanceFactory} bridge that
 * looks up an implementation of this port. When present, JUnit
 * receives the bean instance the implementation produces; when absent,
 * the bridge falls back to reflection
 * ({@code testClass.getDeclaredConstructor().newInstance()}) so test
 * classes that don't carry {@code @EnableTestBeans} keep the
 * JUnit-default behaviour.
 *
 * <p>The default cdi-module implementation resolves the test instance
 * via {@code CDI.current().select(testClass).get()}. cdi-module's
 * CDI extension first augments the test class with {@code @Dependent}
 * so the lookup returns the real instance rather than a proxy — that
 * way private fields, package-private test classes and JUnit's
 * {@code PER_METHOD} lifecycle remain unchanged for the test author.
 *
 * <p>Under {@code @QuarkusTest} this port is intentionally not
 * provided: Quarkus's own {@code TestInstanceFactory} owns instance
 * creation for the test class. The classpath swap that picks
 * cdi-module vs quarkus-module also decides whether jawelte's factory
 * is on the classpath.
 */
public interface TestInstanceFactoryPort {

    /**
     * Produce the test instance JUnit will use for the given test
     * class. Implementations may return the instance via a CDI
     * lookup, via reflection, or by any other mechanism appropriate
     * to the active runtime; the only contract is that the returned
     * object is an instance of {@code testClass} (or a subclass) and
     * is fully initialised by the time it returns (any framework-
     * driven field injection has already happened).
     *
     * @param testClass the JUnit test class
     * @return the test instance JUnit will invoke {@code @Test}
     *         methods on; never {@code null}
     */
    Object createInstance(Class<?> testClass);
}
