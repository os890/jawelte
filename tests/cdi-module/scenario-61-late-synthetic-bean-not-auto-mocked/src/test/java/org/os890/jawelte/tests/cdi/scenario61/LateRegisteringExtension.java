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
package org.os890.jawelte.tests.cdi.scenario61;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;

/**
 * Stands in for any module that registers synthetic beans — wiremock's
 * endpoints, datasource-module's declarations — and does so <em>after</em>
 * cdi-module in the {@code AfterBeanDiscovery} order.
 *
 * <p>The {@code @Priority} pins this observer after cdi-module's, which
 * is the arrangement the ticket described. It is not what the scenario
 * turns on: the collision happens at either order, because the
 * registration is invisible to {@code getBeans(...)} until
 * {@code AfterBeanDiscovery} is over. What resolves it is
 * {@link PaymentGatewayDeclaration}.
 */
public class LateRegisteringExtension implements Extension {

    /** No-arg constructor required by the CDI runtime. */
    public LateRegisteringExtension() {
    }

    void onAfterBeanDiscovery(@Observes @Priority(9000) AfterBeanDiscovery event) {
        event.addBean()
                .types(PaymentGateway.class, Object.class)
                .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                .scope(Dependent.class)
                .produceWith(instance -> (PaymentGateway) () -> "late-extension");
    }
}
