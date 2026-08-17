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
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;

import org.os890.jawelte.core.api.SuppliedTypeRegistry;
import org.os890.jawelte.core.api.port.TestContext;

/**
 * Stands in for any module that registers synthetic beans — wiremock's
 * endpoints, datasource-module's declarations — and does so <em>after</em>
 * cdi-module in the {@code AfterBeanDiscovery} order.
 *
 * <p>The {@code @Priority(9000)} pins the registration after
 * cdi-module's auto-mock observer, which is the arrangement the ticket
 * described and the harder half of the contract: a module may register
 * whenever it likes, as long as it has said what it supplies before the
 * reader looks. This one therefore marks the type in
 * {@code BeforeBeanDiscovery} and registers the bean much later — if
 * only the registration order mattered, this scenario could not pass.
 */
public class LateRegisteringExtension implements Extension {

    /** No-arg constructor required by the CDI runtime. */
    public LateRegisteringExtension() {
    }

    void onBeforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        // Said early; done late. The registry decouples the two.
        SuppliedTypeRegistry.of(TestContext.get()).markSupplied(PaymentGateway.class);
    }

    void onAfterBeanDiscovery(@Observes @Priority(9000) AfterBeanDiscovery event) {
        event.addBean()
                .types(PaymentGateway.class, Object.class)
                .qualifiers(Default.Literal.INSTANCE, Any.Literal.INSTANCE)
                .scope(Dependent.class)
                .produceWith(instance -> (PaymentGateway) () -> "late-extension");
    }
}
