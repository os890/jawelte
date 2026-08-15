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

import java.lang.reflect.Type;
import java.util.Set;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.api.port.SyntheticBeanTypeDeclaration;

/**
 * The other half of {@link LateRegisteringExtension}: what the extension
 * is going to register, said out loud so auto-mocking can stay off it.
 *
 * <p>An extension cannot be detected doing this. Its {@code addBean()}
 * call is invisible to {@code BeanManager.getBeans(...)} for the whole
 * of {@code AfterBeanDiscovery}, so the check auto-mock uses to decide
 * whether an injection point still needs a mock answers "unsatisfied"
 * no matter which observer runs first — and a mock of the same type and
 * qualifiers joins the deployment.
 *
 * <p>Unconditional here because the extension registers unconditionally.
 * A module that only registers when it discovered something (as
 * datasource-module does) has to declare on the same condition.
 */
public class PaymentGatewayDeclaration implements SyntheticBeanTypeDeclaration {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public PaymentGatewayDeclaration() {
    }

    @Override
    public Set<Type> declaredTypes(TestContext testContext) {
        return Set.of(PaymentGateway.class);
    }
}
