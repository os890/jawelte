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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;

/**
 * Spells the same request as {@link OrderService} with {@code @Any}
 * written out, on the bean side rather than the test side.
 */
@ApplicationScoped
public class AnyInjectingService {

    @Inject
    @Any
    private PricingService pricingService;

    public AnyInjectingService() {
    }

    /**
     * @return the collaborator
     */
    public PricingService collaborator() {
        return pricingService;
    }
}
