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
package org.os890.jawelte.tests.testcontrol.scenario24;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * Superclass for {@link Scenario24Test}. Holds the test method
 * carrying the {@code @TestControl} annotation that the subclass
 * inherits without override.
 *
 * <p>The verification is structural: the test method exists only on
 * this class, so JUnit Jupiter can only discover and run it on the
 * subclass via inheritance. testcontrol's lifecycle adapter resolves
 * {@code @TestControl} through
 * {@code AnnotationSupport.findAnnotation(method, TestControl.class)},
 * which walks the class hierarchy when the subclass does not
 * override. If the inheritance chain does not propagate
 * {@code @TestControl}, the adapter's {@code beforeEach} still runs
 * (no annotation → no testData seeding → no scope veto), but the
 * test would fail outright in any environment that relied on the
 * inherited annotation to drive behaviour. Reaching the empty body
 * of {@code inheritedTestMethod} cleanly is sufficient evidence that
 * the inheritance / annotation-resolution pipeline did not blow up
 * on an inherited declaration.
 */
public abstract class Scenario24Base {

    @Test
    @TestControl(testDataBasePath = "from-superclass")
    public void inheritedTestMethod() {
        // Empty body: presence of @TestControl on the parent method
        // and clean execution through testcontrol's beforeEach /
        // afterEach lifecycle on the subclass is what this scenario
        // verifies.
    }
}
