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

import org.os890.jawelte.core.api.ConfigBean;
import org.os890.jawelte.core.api.port.BeanScopeMapper;
import org.os890.jawelte.module.scope.api.TestClassScoped;

/**
 * {@link BeanScopeMapper} provider that rewrites
 * {@link ConfigBean @ConfigBean}-stereotyped beans to
 * {@link TestClassScoped @TestClassScoped}.
 *
 * <p>{@code @ConfigBean} is a stereotype meta-annotated with
 * {@code @ApplicationScoped} — config beans default to
 * application scope. For test runs jawelte prefers per-test-class
 * lifetime so configuration state is freshly evaluated each test
 * class.
 *
 * <p>{@link #preserveExplicitDirectScopes()} returns
 * {@code true}: when the user has declared an explicit non-default
 * scope alongside {@code @ConfigBean} (e.g.
 * {@code @ConfigBean @RequestScoped class}), the remap is
 * skipped and the user's choice is honoured.
 *
 * <p>This also applies to a scope <em>inherited</em> from a base
 * class: because the built-in CDI scopes are {@code @Inherited},
 * {@code @ConfigBean class Sub extends @RequestScoped Base} resolves
 * (per CDI's own rules) to {@code @RequestScoped}, so the remap to
 * {@code @TestClassScoped} is skipped and {@code Sub} keeps the
 * base's scope. If {@code @TestClassScoped} is wanted on such a
 * subclass, declare it directly on the subclass
 * ({@code @ConfigBean @TestClassScoped class Sub extends Base}); a
 * directly-declared scope overrides the base's inherited one.
 */
public class ConfigBeanToTestClassScoped implements BeanScopeMapper {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public ConfigBeanToTestClassScoped() {
    }

    @Override
    public Class<? extends Annotation> trigger() {
        return ConfigBean.class;
    }

    @Override
    public Class<? extends Annotation> targetScope() {
        return TestClassScoped.class;
    }

    @Override
    public boolean preserveExplicitDirectScopes() {
        return true;
    }
}
