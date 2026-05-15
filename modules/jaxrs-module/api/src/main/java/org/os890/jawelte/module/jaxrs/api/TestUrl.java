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
package org.os890.jawelte.module.jaxrs.api;

import java.util.function.Supplier;

/**
 * Injectable supplier of the embedded JAX-RS server's base URL, with
 * the OS-resolved port substituted in. The URL has the shape
 * {@code "http://localhost:{port}"} where {@code {port}} is the
 * actual bound port chosen by the OS when
 * {@code JaxRsLifecycleAdapter.beforeAll} called
 * {@code SeBootstrap.start} with port {@code 0}.
 *
 * <p>The interface extends {@link Supplier Supplier&lt;String&gt;} so
 * the same instance can be passed to client-side helpers that expect
 * a {@code Supplier<String>} without an adapter.
 *
 * <p>Typical use:
 * <pre>{@code
 * @Inject TestUrl testUrl;
 *
 * @Test
 * void getsHello() {
 *     try (Response response = ClientBuilder.newClient()
 *             .target(testUrl.get() + "/hello")
 *             .request()
 *             .get()) {
 *         assertThat(response.getStatus()).isEqualTo(200);
 *     }
 * }
 * }</pre>
 *
 * <p>The implementing bean ({@code TestUrlHolder}, in
 * jaxrs-module/impl) is {@code @ApplicationScoped} by default; when
 * testcontrol-module is on the classpath, jaxrs-module's CDI
 * Extension upgrades it to {@code @TestClassScoped} so the lifetime
 * lines up with the per-test-class server lifetime. Under
 * cdi-module's per-test-class container, the two scopes are
 * observably equivalent (one URL per test class either way).
 */
public interface TestUrl extends Supplier<String> {

    /**
     * Returns the embedded server's base URL with the resolved
     * port, e.g. {@code "http://localhost:54321"}. The exact port
     * is OS-assigned when {@code SeBootstrap.start} is called in
     * {@code beforeAll} and stays constant for the lifetime of the
     * test class.
     *
     * @return the base URL; never {@code null}
     * @throws IllegalStateException if called before the embedded
     *         server is up — message
     *         {@code "JAX-RS server not started yet"}
     */
    @Override
    String get();
}
