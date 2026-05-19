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
 * SPI for modules that need to participate in the ArC build pipeline.
 * Discovered via {@code ServiceLoader}; called from
 * {@code ArcCdiContainerPort} after {@code BeforeBeanDiscovery} fires
 * on portable extensions and before {@code BeanProcessor.process()}
 * runs.
 *
 * <p>ArC under standalone use does not dispatch
 * {@code ProcessAnnotatedType} to portable extensions and never
 * invokes legacy {@code AfterBeanDiscovery.addBean(...)} for synthetic
 * beans. Every module that relied on those portable phases registers
 * an {@code ArcContextContributor} alongside its portable extension
 * to perform the same work via ArC's native build surfaces:
 *
 * <ul>
 *   <li>{@code builder.addAnnotationTransformation(...)} — replaces
 *       {@code ProcessAnnotatedType} mutations
 *       (see ejb-module's class-level scope rewriting).</li>
 *   <li>{@code builder.addBeanRegistrar(...)} — replaces
 *       {@code AfterBeanDiscovery.addBean(...)} (see wiremock-module
 *       and spring-data-module's synthetic-bean registration).</li>
 *   <li>{@code builder.addInterceptorBindingRegistrar(...)} —
 *       replaces {@code BeforeBeanDiscovery.addInterceptorBinding(...)}
 *       (see jpa-module's {@code @Transactional} +
 *       {@code @ReadOnly} registration).</li>
 *   <li>{@code builder.addContextRegistrar(...)} — replaces
 *       {@code AfterBeanDiscovery.addContext(...)} (see scope-module's
 *       {@code @TestClassScoped} / {@code @TestMethodScoped}
 *       registration).</li>
 *   <li>{@code builder.addExcludeType(...)} — replaces
 *       {@code ProcessAnnotatedType.veto()} (see jta-module's
 *       vendor-bean veto).</li>
 * </ul>
 *
 * <p>Contributors that register synthetic beans for IPs should also
 * pre-register the resulting {@code (type, qualifiers)} shapes via
 * {@code JaweltAutoMockBuildCompatibleExtension.preRegisterExistingBeanShape}
 * so the auto-mock BCE doesn't add a parallel auto-mock for the same
 * IPs at {@code @Synthesis} time.
 */
public interface ArcContextContributor {

    /**
     * Run the contributor's work against the in-flight
     * {@link BeanProcessor.Builder}. The {@link TestContext} carries
     * the active test class so the contributor can inspect
     * annotations (e.g. {@code @PersistenceConfig},
     * {@code @EnableWireMock}, {@code @TestControl}).
     *
     * @param testContext the framework's active test context; never
     *                    {@code null}
     * @param builder     the ArC builder; never {@code null}
     */
    void contribute(TestContext testContext, BeanProcessor.Builder builder);
}
