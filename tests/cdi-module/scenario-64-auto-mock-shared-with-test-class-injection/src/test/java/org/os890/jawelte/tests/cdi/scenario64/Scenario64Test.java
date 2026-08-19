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
package org.os890.jawelte.tests.cdi.scenario64;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Scenario 63 pins deduplication between two application beans. This
 * one pins it across the boundary that used to break it: the same
 * unqualified unsatisfied type injected by an application bean and by
 * the test class.
 *
 * <p>The deployment used to fail outright here. Auto-mock candidates
 * are keyed by {@code (targetType, qualifiers)}, and a plain
 * {@code @Inject} was keyed as {@code @Default} inside a bean but as
 * the empty set on the test class, so two {@code @Default} beans were
 * registered. Issue 155 normalized the test-class walk.
 */
@EnableTestBeans
class Scenario64Test {

    @Inject
    private Greeter greeter;

    @Inject
    private AuditService auditService;

    @Test
    void theBeanAndTheTestClassResolveToTheSameMock() {
        assertThat(auditService)
                .as("a plain @Inject must be keyed as @Default on both collection paths, so the "
                        + "bean's injection point and the test class's share one synthetic bean")
                .isSameAs(greeter.collaborator());
    }

    @Test
    void theSharedMockAnswersNullUntilItIsStubbed() {
        assertThat(auditService.audit("anything"))
                .as("an unstubbed mock answers the type default")
                .isNull();
    }
}
