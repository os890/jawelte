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
package org.os890.jawelte.tests.quarkus.scenario30;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
@EnableTestBeans
class Scenario30Test extends BaseScenario30 {

    @Inject
    Provider<AuditService> auditProvider;

    @Inject
    Instance<EmailService> emailInstance;

    @Inject
    @Premium
    PaymentService premiumPayments;

    @Inject
    AuditService directAudit;

    @Test
    void allInjectFieldVariantsArePopulatedIncludingInheritedAndWrappedAndQualified() {
        assertThat(inheritedBeacon).isNotNull();
        assertThat(auditProvider).isNotNull();
        assertThat(auditProvider.get()).isNotNull();
        assertThat(emailInstance).isNotNull();
        assertThat(emailInstance.get()).isNotNull();
        assertThat(premiumPayments).isNotNull();
        assertThat(directAudit).isNotNull();
    }
}
