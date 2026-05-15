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
package org.os890.jawelte.module.wiremock.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Meta-annotation placed on a user {@code @Qualifier} annotation to
 * declare it as a WireMock endpoint identity. The user qualifier
 * then doubles as the lookup key for the endpoint's
 * {@code com.github.tomakehurst.wiremock.WireMockServer} and the
 * matching {@code com.github.tomakehurst.wiremock.client.WireMock}
 * stub-registration client.
 *
 * <p>Example:
 * <pre>
 *   &#64;WireMockEndpoint(port = 18081)
 *   &#64;Qualifier
 *   &#64;Retention(RUNTIME)
 *   &#64;Target({FIELD, METHOD, PARAMETER, TYPE})
 *   public &#64;interface PaymentApi { }
 *
 *   &#64;WireMockEndpoint  // port = 0 — OS-assigned
 *   &#64;Qualifier
 *   &#64;Retention(RUNTIME)
 *   &#64;Target({FIELD, METHOD, PARAMETER, TYPE})
 *   public &#64;interface InventoryApi { }
 * </pre>
 *
 * <p>Tests inject the per-endpoint server with the user qualifier:
 * <pre>
 *   &#64;Inject &#64;PaymentApi   WireMockServer paymentServer;
 *   &#64;Inject &#64;InventoryApi WireMock       inventoryStubs;
 * </pre>
 *
 * <p>The annotation hierarchy is walked recursively during endpoint
 * discovery: a qualifier {@code @PaymentService} that is itself
 * meta-annotated {@code @PaymentApi} (which carries
 * {@code @WireMockEndpoint}) resolves to the same endpoint as a
 * direct {@code @PaymentApi} injection.
 *
 * <p>{@code @WireMockEndpoint} is {@code ANNOTATION_TYPE}-only.
 * Placing it on a field, method, or type directly is rejected by
 * the compiler — endpoint identity is always carried by a user
 * {@code @Qualifier} annotation.
 *
 * <p><b>Hex-arch note.</b> Like {@link EnableWireMock}, this type
 * holds no reference to the upstream WireMock library. Endpoint
 * identity is a compile-time CDI qualifier, not a string name;
 * the {@link #port()} attribute is an {@code int}, not a string.
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface WireMockEndpoint {

    /**
     * The TCP port the embedded {@code WireMockServer} for this
     * endpoint binds to.
     *
     * <p>The default {@code 0} requests an OS-assigned ephemeral
     * port — the resolved port is then readable via
     * {@code server.port()} on the injected
     * {@code WireMockServer}. This is the recommended setting
     * for the vast majority of tests: it avoids port-conflict
     * flakiness when multiple test classes (or multiple test
     * suites) run on the same host.
     *
     * <p>A non-zero value pins the server to a specific port. If
     * the port is already in use when {@code beforeAll} runs,
     * the adapter raises {@code RuntimeException} wrapping
     * {@code java.net.BindException} and {@code beforeAll}
     * fails.
     *
     * @return the configured port, or {@code 0} for an
     *         OS-assigned ephemeral port
     */
    int port() default 0;
}
