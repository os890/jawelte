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

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * {@code @Any} where several candidates of the type exist.
 *
 * <p>{@link Scenario68Test} covers the case with exactly one candidate,
 * where {@code @Any} must collapse onto the same mock as a plain
 * {@code @Inject}. This is the other side: two auto-mocks of
 * {@link ShippingCalculator} exist here, one {@code @Default} and one
 * {@code @Audited}.
 *
 * <p>A <em>direct</em> {@code @Inject @Any ShippingCalculator} would be
 * ambiguous, and correctly so — {@code @Any} matches every bean of the
 * type, so asking for one instance when two qualify has no answer. That
 * is a CDI usage error in the test, not a defect in the auto-mock
 * keying, and the container is right to reject the deployment. It is
 * deliberately not asserted here: pinning it would only be pinning that
 * the container enforces its own rules.
 *
 * <p>What is jawelte's business is that the supported spelling works.
 * {@code Instance<T>} is how CDI expresses "all of them", and it has to
 * see the real auto-mocks rather than a fresh one conjured by the
 * module's own {@code Instance<T>} unwrapping.
 */
@EnableTestBeans
class Scenario68AnyOverSeveralCandidatesTest {

    @Inject
    private PlainShippingService plainShippingService;

    @Inject
    private AuditedShippingService auditedShippingService;

    @Inject
    @Any
    private Instance<ShippingCalculator> allCalculators;

    @Test
    void anyInstanceSeesEveryAutoMockOfTheType() {
        assertThat(allCalculators.stream())
                .as("@Any Instance<T> is the supported way to reach several candidates, and must "
                        + "yield exactly the two auto-mocks that exist")
                .containsExactlyInAnyOrder(
                        plainShippingService.collaborator(),
                        auditedShippingService.collaborator());
    }

    @Test
    void theUnwrappingDoesNotConjureAnExtraMock() {
        assertThat(allCalculators.stream())
                .as("auto-mocking unwraps Instance<T> to decide whether the type needs a mock; "
                        + "with candidates already registered it must add nothing")
                .hasSize(2);
    }
}
