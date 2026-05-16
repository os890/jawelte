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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Activates jawelte's WireMock support for a test class. The
 * annotation is meta-annotated with {@link EnableTestBeans} so
 * applying {@code @EnableWireMock} to a test class is enough to
 * activate jawelte's CDI machinery as well — users don't need to
 * write both annotations side-by-side. JUnit Jupiter walks the
 * annotation's meta-annotations to discover the
 * {@code @ExtendWith(EnableTestBeans.Proxy.class)} that
 * {@code @EnableTestBeans} carries, registering jawelte's proxy
 * extension automatically.
 *
 * <p>wiremock-module's {@code WireMockLifecycleAdapter}
 * ({@code @Priority(75)}, same band as jaxrs-module, between
 * testcontrol at 50 and scope at 100) reads this annotation off the
 * test class in {@code beforeAll}, starts one
 * {@code com.github.tomakehurst.wiremock.WireMockServer} per
 * discovered {@link WireMockEndpoint} qualifier (or one default
 * server when no qualifier was discovered), and shuts the servers
 * down in {@code afterAll}. The user injects WireMock library types
 * directly — {@code @Inject WireMock} (the stub registration
 * client) and {@code @Inject WireMockServer} (the full server
 * handle, used for {@code server.port()} / {@code server.baseUrl()}
 * metadata reads). Multi-endpoint tests qualify both injection
 * sites with the test's own {@code @Qualifier} annotation
 * meta-annotated with {@link WireMockEndpoint}.
 *
 * <p>The annotation carries <b>no attributes</b>. Endpoint
 * configuration (fixed port vs. OS-assigned) lives entirely on the
 * {@link WireMockEndpoint} meta-qualifier — compile-time typed, per
 * the project's type-safety preference. Tests with no qualifier
 * boot a single default endpoint on an OS-assigned port.
 *
 * <p>Stubs are reset between test methods by an explicit
 * {@code WireMockServer.resetAll()} call in the adapter's
 * {@code beforeEach}. This is not driven by CDI scope destruction
 * — even if {@code testcontrol-module} vetoes scopes for a method,
 * stubs are still reset.
 *
 * <p><b>Hex-arch note.</b> No JUnit Jupiter type appears on this
 * api's surface — the JUnit bridge lives entirely in
 * {@code core/api} via {@link EnableTestBeans} (which
 * wiremock-module/api references only as a Java meta-annotation
 * reference, not by depending on JUnit). The upstream WireMock
 * library types ({@code WireMock}, {@code WireMockServer}) do not
 * appear on this api jar either — the api↔library bridge lives in
 * wiremock-module/impl's CDI producer. The api jar consists of
 * exactly two annotation types: {@code @EnableWireMock} and
 * {@link WireMockEndpoint}.
 *
 * <p>{@code @EnableWireMock} is {@code TYPE}-only. Placing it on a
 * method or any other element is rejected by the compiler.
 *
 * <p><b>Inheritance.</b> The annotation is meta-annotated with
 * {@link Inherited @Inherited}: a test class extending a base
 * class that carries {@code @EnableWireMock} picks the
 * activation up without re-declaring it. The lifecycle adapter's
 * {@code testClass.getAnnotation(EnableWireMock.class)} probe
 * walks the class hierarchy and sees the inherited annotation;
 * JUnit Jupiter likewise discovers the meta-annotated
 * {@code @ExtendWith(EnableTestBeans.Proxy.class)} through the
 * inheritance chain. Useful for shared-setup base classes that
 * a family of test classes extends.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@EnableTestBeans
public @interface EnableWireMock {
}
