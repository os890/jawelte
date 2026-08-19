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

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Not a test of its own - run through {@code EngineTestKit} by
 * {@link Scenario64Test}, because the deployment it describes fails
 * before any test method runs.
 *
 * <p>{@code AuditService} is unsatisfied and injected twice: once by
 * {@link Greeter} and once here. Both are plain {@code @Inject}s, and
 * that is what makes them collide - the bean's injection point is keyed
 * from CDI's normalized qualifier set ({@code @Default}) while this
 * class's field is walked reflectively and keyed from the annotations
 * actually written on it (none). The keys differ, so two
 * {@code @Default} beans are registered and the container refuses to
 * deploy.
 */
@EnableTestBeans
class CollidingSubject {

    @Inject
    private Greeter greeter;

    @Inject
    private AuditService auditService;

    @Test
    void neverRunsBecauseTheContainerDoesNotDeploy() {
        throw new AssertionError("unreachable: the deployment fails in beforeAll");
    }
}
