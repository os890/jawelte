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
import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;
import org.os890.jawelte.core.api.port.BeanScopeMapper;

/**
 * {@link BeanScopeMapper} provider that remaps types carrying
 * the impl-internal {@link WireMockManagedScope} marker to the
 * CDI scope configured under MP Config key
 * {@value #TARGET_SCOPE_KEY}.
 *
 * <p><b>Default value.</b> scope-module/impl's
 * {@code microprofile-config.properties} ships the key with
 * value {@code org.os890.jawelte.module.scope.api.TestClassScoped}.
 * Consumers override by setting the same key in any
 * higher-priority MP Config source.
 *
 * <p><b>Reflective load, no compile-time scope-module dep.</b>
 * The configured class is resolved once at class-load time via
 * {@link Class#forName(String, boolean, ClassLoader)}. When the
 * key is unset, or the configured class isn't on the runtime
 * classpath (typically scope-module absent),
 * {@link #targetScope()} returns {@code null} and the active
 * {@code BeanScopeMapperPort} skips this provider — the
 * registry keeps its declared {@code @ApplicationScoped} scope.
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
 * on the classpath via {@code ServiceLoader.load(...)} and applies
 * the remap during {@code ProcessAnnotatedType} through
 * {@code ScopeRemapCdiExtension}. A fresh provider is instantiated per
 * container, so the target scope is resolved from the active MP Config
 * layer each time rather than frozen for the JVM.
 */
public class WireMockRegistryScopeRemap implements BeanScopeMapper {

    /**
     * MP Config key whose value is the FQCN of the CDI scope
     * annotation to install on the {@code WireMockServerRegistry}
     * bean. scope-module/impl supplies the default
     * ({@code org.os890.jawelte.module.scope.api.TestClassScoped})
     * via its {@code microprofile-config.properties}.
     */
    public static final String TARGET_SCOPE_KEY =
            "org.os890.jawelte.module.wiremock.registry.default-scope";

    // Resolved per instance — and a fresh provider is created per
    // container — so each container picks up its own MP Config layer
    // rather than the value frozen by the first container in the JVM.
    private final Class<? extends Annotation> targetScope = loadTargetScope();

    /** No-arg constructor required by {@code ServiceLoader}. */
    public WireMockRegistryScopeRemap() {
    }

    @Override
    public Class<? extends Annotation> trigger() {
        return WireMockManagedScope.class;
    }

    @Override
    public Class<? extends Annotation> targetScope() {
        return targetScope;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> loadTargetScope() {
        Optional<String> configured = ConfigProvider.getConfig()
                .getOptionalValue(TARGET_SCOPE_KEY, String.class)
                .map(String::trim)
                .filter(value -> !value.isEmpty());
        if (configured.isEmpty()) {
            return null;
        }
        try {
            Class<?> loaded = Class.forName(
                    configured.get(),
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
