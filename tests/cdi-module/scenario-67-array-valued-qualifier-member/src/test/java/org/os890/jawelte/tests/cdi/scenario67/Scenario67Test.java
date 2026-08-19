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
package org.os890.jawelte.tests.cdi.scenario67;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Qualifier keying when a member is array-valued.
 *
 * <p>Scenario 07 pins a {@code @Nonbinding} scalar and scenario 54 a
 * binding scalar. Neither uses an array, and the auto-mock key hashes
 * member values with {@code value.hashCode()} — an identity hash for an
 * array, and an unstable one, because Java's annotation proxy hands out
 * a fresh clone on every access. The gap is narrower than it looks:
 * CDI 4.1 makes a binding array member a definition error, so the only
 * array a qualifier may carry is {@code @Nonbinding}, and those are
 * skipped by both the equality check and the hash. This scenario pins
 * that they really are skipped — including when the two arrays differ
 * in length, not just in contents.
 */
@EnableTestBeans
class Scenario67Test {

    @Inject
    private OrderService orderService;

    @Inject
    private ShippingService shippingService;

    @Inject
    private WholesaleService wholesaleService;

    @Test
    void theNonbindingArrayMemberIsIgnored() {
        assertThat(orderService.collaborator())
                .as("the two injection points differ only in a @Nonbinding array member, so they "
                        + "are one key and must resolve to a single synthetic bean")
                .isSameAs(shippingService.collaborator());
    }

    @Test
    void theBindingMemberStillDistinguishes() {
        assertThat(wholesaleService.collaborator())
                .as("ignoring @Nonbinding must not degrade into ignoring everything: a different "
                        + "binding channel is a different key, and gets its own mock")
                .isNotSameAs(orderService.collaborator());
    }

    @Test
    void theSharedMockAnswersNullUntilItIsStubbed() {
        assertThat(orderService.collaborator().priceOf("SKU-1"))
                .as("an unstubbed mock answers the type default")
                .isNull();
    }
}
