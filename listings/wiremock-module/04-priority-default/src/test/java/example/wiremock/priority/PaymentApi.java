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
package example.wiremock.priority;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.annotation.Priority;
import jakarta.inject.Qualifier;

import org.os890.jawelte.module.wiremock.api.WireMockEndpoint;

/**
 * Endpoint qualifier for the payments upstream, carrying the strict
 * minimum {@code @Priority(1)} value among the qualifiers in this
 * test class. wiremock-module's CDI extension reads the priority and
 * marks {@code @PaymentApi}'s synthetic bean as the implicit
 * {@code @Default} — an unqualified {@code @Inject WireMockServer}
 * therefore resolves to {@code @PaymentApi}'s server.
 *
 * <p>The sibling {@link InventoryApi} carries no {@code @Priority};
 * its synthetic bean keeps only the {@code @InventoryApi} qualifier
 * and drops {@code @Default}.
 */
@WireMockEndpoint(port = 0)
@Priority(1)
@Qualifier
@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER, TYPE})
public @interface PaymentApi {
}
