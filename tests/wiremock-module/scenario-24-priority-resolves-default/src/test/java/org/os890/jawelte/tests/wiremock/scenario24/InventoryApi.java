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
package org.os890.jawelte.tests.wiremock.scenario24;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;

import org.os890.jawelte.module.wiremock.api.WireMockEndpoint;

/**
 * Fixed-port endpoint qualifier without {@code @Priority}. Its
 * effective priority is therefore the implicit lowest precedence,
 * so it loses the implicit {@code @Default} to {@link PaymentApi}
 * (which carries {@code @Priority(1)}). Qualified injection of
 * {@code @InventoryApi WireMockServer} continues to work via
 * standard CDI qualifier resolution.
 */
@WireMockEndpoint(port = 19102)
@Qualifier
@Retention(RUNTIME)
@Target({FIELD, METHOD, PARAMETER, TYPE})
public @interface InventoryApi {
}
