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
 *
 * <p>The module ships {@code mock-maker-subclass}, which is what lets
 * the shipped Mockito factory produce a competing bean at all: without
 * it the inline mock maker cannot self-attach its agent under this JDK,
 * auto-mock registers nothing, and this scenario would pass whatever
 * the code did (#150).
 */
package org.os890.jawelte.tests.cdi.scenario61;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Auto-mock exists to satisfy injection points nothing else satisfies.
 * A bean another extension is about to register is not such a case —
 * but "about to" is the problem: the candidate set is collected at
 * {@code ProcessInjectionPoint}, and the re-check that would clear it
 * runs on {@code BeanManager.getBeans(...)}, which does not see
 * {@code addBean()} registrations while {@code AfterBeanDiscovery} is
 * still in progress. Measured on OpenWebBeans and on Weld, at every
 * observer priority — so no ordering makes the re-check notice them.
 *
 * <p>{@link PaymentGatewayContribution} is what closes it: the extension
 * declares the type it supplies, and auto-mock leaves it alone. Remove
 * that declaration and this scenario fails at deployment with
 * {@code AmbiguousResolutionException} naming two beans of the same type
 * and qualifiers, which is the shape reported in #124.
 */
@EnableTestBeans
class Scenario61Test {

    @Inject
    CheckoutService checkoutService;

    @Test
    void theLateSyntheticBeanWinsAndNoMockCompetesWithIt() {
        assertThat(checkoutService.gatewayOrigin())
                .as("the injection point is satisfied by the registered bean, "
                        + "so nothing should have auto-mocked it")
                .isEqualTo("late-extension");
    }
}
