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
package org.os890.jawelte.tests.cdi.scenario68;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * The guard on {@link Scenario68Test}: dropping {@code @Any} from the
 * auto-mock key must not slide into dropping qualifiers generally.
 *
 * <p>It works on {@link ShippingCalculator} rather than
 * {@link PricingService} on purpose. Every bean class in this module is
 * discovered by every container in it, and {@code PricingService} is
 * injected as bare {@code @Any} by {@link AnyInjectingService} — giving
 * it a second mock would make that injection point ambiguous by the
 * specification rather than by any defect.
 */
@EnableTestBeans
class Scenario68QualifierStillSeparatesTest {

    @Inject
    private PlainShippingService plainShippingService;

    @Inject
    private AuditedShippingService auditedShippingService;

    @Test
    void aRealQualifierStillGetsItsOwnMock() {
        assertThat(auditedShippingService.collaborator())
                .as("@Audited is a genuine narrowing, so it must key separately from the plain "
                        + "@Inject and receive a mock of its own")
                .isNotSameAs(plainShippingService.collaborator());
    }

    @Test
    void bothMocksAnswerNullUntilStubbed() {
        assertThat(plainShippingService.collaborator().costOf("SKU-1")).isNull();
        assertThat(auditedShippingService.collaborator().costOf("SKU-1")).isNull();
    }
}
