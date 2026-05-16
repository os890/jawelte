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
package org.os890.jawelte.module.wiremock.impl.adapter.extension.remap;

import java.lang.annotation.Annotation;

import org.os890.jawelte.core.api.port.BeanScopeMapper;

/**
 * {@link BeanScopeMapper} provider that remaps types carrying
 * the impl-internal {@link WireMockManagedScope} marker to
 * {@code org.os890.jawelte.module.scope.api.TestClassScoped}.
 *
 * <p>Used by wiremock-module to upgrade the per-class
 * {@code WireMockServerRegistry} bean's scope so the registry's
 * lifetime matches the {@code WireMockServer} lifetime (started in
 * {@code beforeAll}, stopped in {@code afterAll}). Under
 * cdi-module's per-test-class CDI container the upgrade is
 * observably equivalent to {@code @ApplicationScoped}; the explicit
 * remap is a cosmetic guarantee for a future per-method CDI
 * container — the same rationale jaxrs-module documents for its
 * own scope-upgrade pattern.
 *
 * <p><b>Target loaded reflectively, no compile-time dep on
 * scope-module.</b> {@link #targetScope()} resolves
 * {@code TestClassScoped} via {@link Class#forName(String)} —
 * usually the project avoids reflection, but here it pays for
 * itself: wiremock-module/impl no longer compile-depends on
 * scope-module/api. If {@code TestClassScoped} is not on the
 * runtime classpath (scope-module absent), the method returns
 * {@code null} and {@code BeanScopeMapperPort} skips the mapping
 * entirely — the registry then keeps its declared
 * {@code @ApplicationScoped} scope.
 *
 * <p>{@link #preserveExplicitDirectScopes()} returns {@code false}
 * (inherited default): {@code @WireMockManagedScope} is an
 * impl-private marker placed only on the wiremock-module-owned
 * registry, so user override semantics don't apply.
 *
 * <p>Discovered via
 * {@code META-INF/services/org.os890.jawelte.core.api.port.BeanScopeMapper}
 * shipped in this module. The active
 * {@code BeanScopeMapperPort} (default impl lives in
 * {@code core/impl}) walks every {@code BeanScopeMapper} provider
 * on the classpath via {@code ServiceLoader.load(...)} once at
 * extension load time and applies the remap during
 * {@code ProcessAnnotatedType} through {@code ScopeRemapCdiExtension}.
 */
public class WireMockRegistryScopeRemap implements BeanScopeMapper {

    private static final String TEST_CLASS_SCOPED_FQCN =
            "org.os890.jawelte.module.scope.api.TestClassScoped";

    private static final Class<? extends Annotation> TEST_CLASS_SCOPED = loadTestClassScoped();

    /** No-arg constructor required by {@code ServiceLoader}. */
    public WireMockRegistryScopeRemap() {
    }

    @Override
    public Class<? extends Annotation> trigger() {
        return WireMockManagedScope.class;
    }

    @Override
    public Class<? extends Annotation> targetScope() {
        return TEST_CLASS_SCOPED;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> loadTestClassScoped() {
        try {
            Class<?> loaded = Class.forName(
                    TEST_CLASS_SCOPED_FQCN,
                    true,
                    WireMockRegistryScopeRemap.class.getClassLoader());
            if (!Annotation.class.isAssignableFrom(loaded)) {
                return null;
            }
            return (Class<? extends Annotation>) loaded;
        } catch (ClassNotFoundException | LinkageError missing) {
            return null;
        }
    }
}
