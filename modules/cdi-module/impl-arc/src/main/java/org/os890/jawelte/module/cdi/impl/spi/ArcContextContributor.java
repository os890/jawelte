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
package org.os890.jawelte.module.cdi.impl.spi;

import org.os890.jawelte.core.api.port.TestContext;

import io.quarkus.arc.processor.BeanProcessor;

/**
 * SPI by which downstream modules contribute build-time configuration
 * to the ArC {@link BeanProcessor} that cdi-module's
 * {@code CdiTestBeanContainer} builds for the current test class.
 * Typical contributions: custom CDI {@code Context} registrations
 * (scope-module's {@code @TestClassScoped} / {@code @TestMethodScoped}),
 * extra {@code BeanRegistrar}s, or {@code AnnotationTransformation}s.
 *
 * <p>Implementations are discovered via {@code ServiceLoader} from
 * {@code META-INF/services/org.os890.jawelte.module.cdi.impl.spi.ArcContextContributor};
 * {@code CdiTestBeanContainer.beforeAll} loads them in
 * {@code @Priority}-ascending order and invokes
 * {@link #contribute(TestContext, BeanProcessor.Builder)} for each
 * before {@code BeanProcessor.process()} runs.
 *
 * <p>The contributor receives the live {@link TestContext} so it can
 * publish per-test-class state (stores, controllers, etc.) for the
 * matching {@code TestModuleLifecyclePort} adapter to drive at
 * {@code beforeEach} / {@code afterEach} / {@code afterAll}.
 *
 * <p>Why this exists instead of CDI portable extensions: Quarkus ArC
 * does not support {@code jakarta.enterprise.inject.spi.Extension}
 * portable extensions. cdi-module/impl ships a minimal bridge for the
 * {@code BeforeBeanDiscovery} and {@code AfterDeploymentValidation}
 * phases, but {@code AfterBeanDiscovery.addContext(...)} — the
 * canonical place to register custom contexts — has no runtime
 * equivalent; contexts in ArC are build-time entities registered via
 * {@code ContextRegistrar} on the {@code BeanProcessor}. This SPI is
 * the ArC-native replacement.
 */
public interface ArcContextContributor {

    /**
     * Contribute to the ArC {@link BeanProcessor.Builder} that
     * cdi-module is preparing for the current test class.
     *
     * @param testContext the live test context for the current run;
     *                    contributors typically bind metadata that
     *                    their matching lifecycle adapter looks up
     *                    later
     * @param builder     the in-progress {@code BeanProcessor.Builder};
     *                    contributors call methods such as
     *                    {@code addContextRegistrar} or
     *                    {@code addBeanRegistrar} on it
     */
    void contribute(TestContext testContext, BeanProcessor.Builder builder);
}
