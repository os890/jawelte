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
package org.os890.jawelte.tests.jaxrs.scenario12;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.jaxrs.api.EnableJaxRs;

/**
 * Misconfigured subject: carries {@code @EnableJaxRs} but
 * <em>not</em> {@code @EnableTestBeans}. The
 * {@code EnableJaxRs.Validator} {@code @ExtendWith} hook fires in
 * {@code beforeAll}, detects the missing companion annotation, and
 * raises {@code IllegalStateException} — failing the class before
 * any test runs.
 *
 * <p>Discovered only by {@link Scenario12Test}'s
 * {@code EngineTestKit} launch (Surefire skips this class because
 * the name doesn't end in {@code Test}).
 */
@EnableJaxRs(restResources = {Scenario12HelloResource.class})
public class Scenario12Subject {

    /** Default no-arg constructor required by JUnit. */
    public Scenario12Subject() {
    }

    /**
     * Body would never execute — the lifecycle bails out in
     * {@code beforeAll} before any test method runs.
     */
    @Test
    public void neverRuns() {
        // Intentionally empty.
    }
}
