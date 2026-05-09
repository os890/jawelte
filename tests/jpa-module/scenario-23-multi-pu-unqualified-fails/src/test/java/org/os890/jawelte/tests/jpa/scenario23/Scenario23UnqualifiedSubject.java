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
package org.os890.jawelte.tests.jpa.scenario23;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * The "subject" test class — driven via JUnit Platform Test Kit by
 * {@link Scenario23Test}. Surefire's default {@code *Test.java} filter excludes
 * this class (no {@code Test} suffix) so it does not run during the normal
 * test run.
 *
 * <p>Has an unqualified {@code @Inject EntityManager} on the test instance plus
 * a CDI bean with the same shape — under the multi-PU configuration this must
 * fail container deployment.
 */
@EnableTestBeans
public class Scenario23UnqualifiedSubject {

    @Inject
    @SuppressWarnings("unused")
    private UnqualifiedConsumer consumer;

    /** No-arg constructor for CDI / JUnit. */
    public Scenario23UnqualifiedSubject() {
    }

    /** Body never runs — container deployment must already have failed. */
    @Test
    public void shouldNeverRun() {
    }
}
