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
package org.os890.jawelte.module.jaxrs.impl.adapter.extension.remap;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.os890.jawelte.core.api.port.BeanScopeMapper;

/**
 * Impl-internal marker annotation declared on the
 * {@code TestUrlHolder} bean. {@link TestUrlScopeRemap} (the
 * {@link BeanScopeMapper} provider shipped by this module) triggers
 * on this marker and remaps the bean's scope to
 * {@code @TestClassScoped} at {@code ProcessAnnotatedType} time —
 * the actual rewrite is driven by core/impl's
 * {@code ScopeRemapCdiExtension}.
 *
 * <p>The remap is deliberately keyed on this marker rather than on
 * {@code @ApplicationScoped}: only the single bean that carries
 * {@code @JaxRsManagedScope} is upgraded, so user-defined
 * {@code @ApplicationScoped} beans (and jaxrs-module's own
 * {@code CdiIntegrationFilter}) are never touched.
 *
 * <p>This annotation is impl-only — not part of the jaxrs-module
 * public contract. It exists solely so {@code TestUrlHolder}'s scope
 * upgrade routes through the same generic SPI used by every other
 * jawelte scope remap (scope-module's {@code @ConfigBean} /
 * {@code @SessionScoped} remaps and wiremock-module's registry remap
 * use the identical shape).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface JaxRsManagedScope {
}
