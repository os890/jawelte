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
package org.os890.jawelte.tests.scope.scenario32;

import java.lang.annotation.Annotation;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;

import org.os890.jawelte.core.api.ConfigBean;
import org.os890.jawelte.core.api.port.BeanScopeMapper;

/**
 * Test-only {@link BeanScopeMapper} that shares the {@code @ConfigBean}
 * trigger with scope-module's built-in
 * {@code ConfigBeanToTestClassScoped} (which targets
 * {@code @TestClassScoped} and carries no {@code @Priority}), but
 * targets {@link RequestScoped} and declares a low-numeric
 * {@code @Priority(1)} so it sorts ahead of the built-in.
 *
 * <p>Registered via {@code META-INF/services} so the active
 * {@code BeanScopeMapperPort} discovers it. The point of the scenario
 * is that the port resolves the provider list through
 * {@code ServicePriorityResolver}, so this higher-precedence provider
 * wins the shared trigger — the documented "ship your own
 * higher-priority BeanScopeMapper to override a built-in remap"
 * contract.
 */
@Priority(1)
public class TestScenarioConfigBeanToRequestScopedMapper implements BeanScopeMapper {

    @Override
    public Class<? extends Annotation> trigger() {
        return ConfigBean.class;
    }

    @Override
    public Class<? extends Annotation> targetScope() {
        return RequestScoped.class;
    }
}
