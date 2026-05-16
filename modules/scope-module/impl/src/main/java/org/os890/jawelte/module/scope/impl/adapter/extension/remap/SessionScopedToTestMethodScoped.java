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
package org.os890.jawelte.module.scope.impl.adapter.extension.remap;

import java.lang.annotation.Annotation;

import jakarta.enterprise.context.SessionScoped;

import org.os890.jawelte.core.api.port.BeanScopeMapper;
import org.os890.jawelte.module.scope.api.TestMethodScoped;

/**
 * {@link BeanScopeMapper} provider that rewrites
 * {@link SessionScoped @SessionScoped} beans to
 * {@link TestMethodScoped @TestMethodScoped}.
 *
 * <p>Web-tier session semantics don't fit the per-test-method-
 * or-class CDI container jawelte boots — "session" effectively
 * maps to "the lifetime of one test method" in this setting,
 * which is the useful test analogue.
 *
 * <p>{@link #preserveExplicitDirectScopes()} returns
 * {@code false} (inherited default) — the trigger
 * {@code @SessionScoped} IS the scope being remapped, so there
 * is no separate user-override semantic to preserve.
 */
public class SessionScopedToTestMethodScoped implements BeanScopeMapper {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public SessionScopedToTestMethodScoped() {
    }

    @Override
    public Class<? extends Annotation> trigger() {
        return SessionScoped.class;
    }

    @Override
    public Class<? extends Annotation> targetScope() {
        return TestMethodScoped.class;
    }
}
