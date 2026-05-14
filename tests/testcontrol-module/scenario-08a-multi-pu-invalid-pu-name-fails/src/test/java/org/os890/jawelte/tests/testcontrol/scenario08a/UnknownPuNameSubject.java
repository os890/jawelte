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
package org.os890.jawelte.tests.testcontrol.scenario08a;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.testcontrol.api.TestControl;

/**
 * Subject test class driven by {@link Scenario08aTest} through
 * {@code EngineTestKit}. Carries a {@code @TestControl(testData=…)}
 * whose entry uses a {@code puName:} prefix pointing at a
 * persistence unit that is NOT declared in
 * {@code META-INF/persistence.xml}. The CDI lookup for the named
 * {@code EntityManager} bean fails fast inside testcontrol's seed
 * transaction template, failing the test method with an exception
 * that points at the offending PU name.
 *
 * <p>Not run by Surefire's default discovery (class name has no
 * {@code Test} prefix/suffix); only EngineTestKit's
 * {@code selectClass} picks it up.
 */
@EnableTestBeans
public class UnknownPuNameSubject {

    public UnknownPuNameSubject() {
    }

    @Test
    @TestControl(testData = "thisPersistenceUnitIsNotDeclared:testdata/scenario08a", requireDbExpected = false)
    void shouldFailBecausePuNameIsUnknown() {
        // Never reached — testcontrol's beforeEach raises an
        // exception while resolving the EntityManager for the
        // unknown PU name.
    }
}
