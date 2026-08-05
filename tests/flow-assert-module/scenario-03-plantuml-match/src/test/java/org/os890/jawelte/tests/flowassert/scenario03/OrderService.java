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
package org.os890.jawelte.tests.flowassert.scenario03;

import java.math.BigDecimal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/** The entry point of the recorded flow: prices an order and audits it. */
@ApplicationScoped
public class OrderService {

    @Inject
    private PricingService pricingService;

    @Inject
    private AuditService auditService;

    public String placeOrder(String sku, int amount) {
        BigDecimal price = pricingService.priceOf(sku);
        auditService.log("ordered " + amount + " of " + sku);
        return sku + "@" + price;
    }
}
