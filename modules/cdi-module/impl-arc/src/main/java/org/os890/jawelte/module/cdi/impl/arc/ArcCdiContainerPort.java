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
package org.os890.jawelte.module.cdi.impl.arc;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.api.port.CdiContainerPort;

/**
 * SKELETON. ArC-backed {@link CdiContainerPort}.
 *
 * <p>Boots ArC at {@code beforeAll}, drives the
 * {@code BeanProcessor.Builder} pipeline including every discovered
 * {@code ArcContextContributor}, then calls {@code Arc.initialize}.
 * Under {@code @QuarkusTest} this port detects that Quarkus owns the
 * container (via {@code isQuarkusTest(testClass)} probe — see
 * {@code DelegatingJUnitExtension}) and degrades to a no-op: Quarkus
 * has already booted ArC, and the framework only contributes module
 * lifecycle hooks plus the auto-mock BCE.
 *
 * <p>Full implementation on the {@code quarkus-full-poc} branch at
 * {@code modules/cdi-module/impl/.../adapter/container/CdiTestBeanContainer.java}.
 * Cherry-pick that class and:
 * <ul>
 *   <li>split it into an ArC-specific port impl (this class) and a
 *       runtime-agnostic skeleton (already in the SE-based
 *       {@code SeContainerCdiContainerPort} on {@code main});</li>
 *   <li>delete the OWB/Weld-shaped paths (those stay in
 *       {@code SeContainerCdiContainerPort});</li>
 *   <li>keep the {@code postProcessTestInstance} / {@code beforeEach}
 *       gating that steps aside under {@code @QuarkusTest};</li>
 *   <li>register against the {@code TestBeanContainerPort} SPI at a
 *       higher {@code @Priority} than the SE port so the ArC backend
 *       wins when both jars are on the classpath.</li>
 * </ul>
 */
public class ArcCdiContainerPort implements CdiContainerPort {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public ArcCdiContainerPort() {
    }

    @Override
    public void start(TestContext testContext) {
        throw new UnsupportedOperationException(
                "ArcCdiContainerPort is a skeleton. Cherry-pick the ArC bootstrap "
                        + "from CdiTestBeanContainer on the quarkus-full-poc branch.");
    }

    @Override
    public void stop(TestContext testContext) {
        throw new UnsupportedOperationException(
                "ArcCdiContainerPort is a skeleton. Cherry-pick the ArC shutdown "
                        + "from CdiTestBeanContainer on the quarkus-full-poc branch.");
    }
}
