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

/**
 * A second unsatisfied type, carrying the "a real qualifier still
 * separates" half of the contract.
 *
 * <p>It has to be a different type from {@link PricingService}. That
 * one is injected as bare {@code @Any} elsewhere in this module, and
 * every bean class here is discovered by every container here, so
 * giving {@code PricingService} a second mock would make that injection
 * point ambiguous by the specification rather than by a defect.
 */
public interface ShippingCalculator {

    /**
     * @param sku the sku
     * @return the shipping cost
     */
    String costOf(String sku);
}
