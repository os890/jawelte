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

import org.os890.jawelte.core.api.TestBean;
import org.os890.jawelte.core.api.port.BeanScopeMapper;
import org.os890.jawelte.module.scope.api.TestClassScoped;

/**
 * {@link BeanScopeMapper} provider consumed by the
 * {@code BeanScopeMapperPort.mapScope(Field)} and
 * {@code BeanScopeMapperPort.mapScope(Method)} overloads. Declares
 * that synthetic beans derived from {@link TestBean @TestBean}-
 * declared static fields and producer methods default to
 * {@link TestClassScoped @TestClassScoped} when scope-module is on
 * the classpath.
 *
 * <p>cdi-module / ejb-module call the port overload, get
 * {@code TestClassScoped.class} back, and use it as the synthetic
 * bean's declared scope. Explicit user scope on the
 * {@code @TestBean} declaration (e.g.
 * {@code @TestBean @RequestScoped Foo foo;}) is handled by the
 * caller: cdi-module checks the field's own scope annotations
 * first and only consults this provider when none is declared.
 * That keeps {@link #preserveExplicitDirectScopes()} at its
 * inherited default of {@code false} — the
 * preserve-explicit-scope logic only applies to the
 * {@code mapScope(Class<?>)} overload (class-level
 * {@code ProcessAnnotatedType} mutation), not to these
 * field / method queries.
 *
 * <p>Discovered via
 * {@code META-INF/services/org.os890.jawelte.core.api.port.BeanScopeMapper}.
 */
public class TestBeanToTestClassScoped implements BeanScopeMapper {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public TestBeanToTestClassScoped() {
    }

    @Override
    public Class<? extends Annotation> trigger() {
        return TestBean.class;
    }

    @Override
    public Class<? extends Annotation> targetScope() {
        return TestClassScoped.class;
    }
}
