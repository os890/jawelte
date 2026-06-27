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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Stereotype;

import org.os890.jawelte.core.api.port.BeanScopeMapper;

/**
 * Impl-internal CDI {@link Stereotype} declared on the
 * {@code TestUrlHolder} bean. It contributes {@link ApplicationScoped}
 * as the default scope, so the holder is {@code @ApplicationScoped}
 * out of the box with no scope-module on the classpath.
 *
 * <p>When scope-module IS present, {@link TestUrlScopeRemap} (the
 * {@link BeanScopeMapper} provider shipped by this module) triggers
 * on this stereotype and rewrites the effective scope to
 * {@code @TestClassScoped} at {@code ProcessAnnotatedType} time —
 * the rewrite is driven by core/impl's {@code ScopeRemapCdiExtension},
 * which adds {@code @TestClassScoped} as a direct class-level scope.
 * Per CDI's "class-level scope wins over stereotype-contributed
 * scope" rule, the directly-added {@code @TestClassScoped} supersedes
 * this stereotype's {@code @ApplicationScoped} default. This is the
 * same shape scope-module's {@code @ConfigBean} stereotype uses.
 *
 * <p>The remap is keyed on this stereotype rather than on
 * {@code @ApplicationScoped}, so only the single bean that carries
 * {@code @JaxRsManaged} is upgraded — user-defined
 * {@code @ApplicationScoped} beans (and jaxrs-module's own
 * {@code CdiIntegrationFilter}) are never touched. The stereotype
 * meta-annotates only {@code @ApplicationScoped} (a core CDI scope),
 * never {@code @TestClassScoped}, so jaxrs-module keeps no
 * compile-time dependency on scope-module; the upgrade target is
 * resolved reflectively by {@link TestUrlScopeRemap}.
 *
 * <p>Impl-only — not part of the jaxrs-module public contract.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Stereotype
@ApplicationScoped
public @interface JaxRsManaged {
}
