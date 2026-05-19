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
package org.os890.jawelte.module.jpa.deployment;

import java.util.List;

import org.jboss.jandex.DotName;

import io.quarkus.arc.deployment.InterceptorBindingRegistrarBuildItem;
import io.quarkus.arc.processor.InterceptorBindingRegistrar;
import io.quarkus.arc.processor.InterceptorBindingRegistrar.InterceptorBinding;
import io.quarkus.deployment.annotations.BuildStep;

/**
 * Quarkus deployment processor for jpa-module. Bridges interceptor
 * bindings that the standalone portable {@code Extension} would
 * register at {@code BeforeBeanDiscovery} — that phase doesn't fire
 * under {@code @QuarkusTest}, so the bindings have to be plumbed
 * through Quarkus's build-time API instead.
 */
public class JpaModuleProcessor {

    private static final DotName READ_ONLY_DOT =
            DotName.createSimple("org.os890.jawelte.module.jpa.api.ReadOnly");

    /** No-arg constructor required by Quarkus's reflective discovery. */
    public JpaModuleProcessor() {
    }

    /**
     * Register {@code @ReadOnly} as an interceptor binding so
     * {@code ReadOnlyInterceptor} is discoverable under
     * {@code @QuarkusTest}. The annotation itself already carries
     * {@code @InterceptorBinding}, but Quarkus's ArC only picks it
     * up when the annotation type sits in the bean archive index;
     * jpa-module/api isn't always indexed automatically, so we
     * register it explicitly.
     */
    @BuildStep
    public InterceptorBindingRegistrarBuildItem registerReadOnlyBinding() {
        return new InterceptorBindingRegistrarBuildItem(new InterceptorBindingRegistrar() {
            @Override
            public List<InterceptorBinding> getAdditionalBindings() {
                return List.of(InterceptorBinding.of(READ_ONLY_DOT));
            }
        });
    }
}
