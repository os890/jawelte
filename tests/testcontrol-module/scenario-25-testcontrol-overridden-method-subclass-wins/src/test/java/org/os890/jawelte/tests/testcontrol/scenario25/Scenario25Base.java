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
package org.os890.jawelte.tests.testcontrol.scenario25;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * Superclass that declares the test method with one
 * {@code @TestControl} value. The subclass overrides the method with
 * a different {@code @TestControl} value; the test asserts the
 * subclass's value is bound on {@code TestContext}, proving the
 * "subclass override wins, no merging" semantic documented on
 * {@link TestControl}.
 */
public abstract class Scenario25Base {

    @Test
    @TestControl(testDataBasePath = "from-superclass-should-be-shadowed")
    public void overriddenTestMethod() {
        // Body is replaced by the subclass override; this declaration
        // exists only to carry the superclass-side @TestControl so the
        // shadowing-on-override semantics can be exercised end-to-end.
    }
}
