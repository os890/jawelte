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
package org.os890.jawelte.tests.lnp.scenario07;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.jaxrs.api.TestUrl;
import org.os890.jawelte.module.testcontrol.api.TestControl;

import io.gatling.app.Gatling$;

/**
 * Drives a meaningful Gatling load against the {@code CustomerResource}
 * hosted by jaxrs-module's embedded server. Each test method spins up
 * a fresh Gatling simulation that injects 10 virtual users running a
 * 5-step CRUD roundtrip — about 50 HTTP calls per class. Gatling's
 * own assertions decide pass/fail; this method just translates the
 * non-zero exit code into a JUnit failure.
 *
 * <p>The numbered subclasses repeat the simulation N=50 times per
 * JVM so the LNP report can compare Gatling-client overhead against
 * scenarios 01-06.
 */
public abstract class AbstractFullCrudGatlingScenarioTest {

    @Inject
    private TestUrl testUrl;

    /** Default constructor required by JUnit/CDI. */
    protected AbstractFullCrudGatlingScenarioTest() {
    }

    @Test
    @TestControl(testData = "lnp-gatling/seed")
    public void gatlingCrudRoundtrip() {
        // Pass the OS-assigned base URL through to the Simulation via
        // a system property — Gatling reflectively instantiates the
        // Simulation class, so we can't set state on the instance
        // directly.
        System.setProperty("gatling.baseUrl", testUrl.get());

        // Gatling 3.13 exposes only Scala-callable signatures on
        // io.gatling.app.Gatling; the Java-friendly entry is the
        // companion object's fromArgs(String[]) method. Using CLI-
        // style args keeps us off Scala collection APIs entirely.
        String[] args = {
                "--simulation", CustomerCrudSimulation.class.getName(),
                "--results-folder",
                "target/gatling/" + getClass().getSimpleName(),
                "--no-reports"
        };
        int exitCode = Gatling$.MODULE$.fromArgs(args);

        assertThat(exitCode)
                .as("Gatling simulation exit code (0 = all assertions passed)")
                .isZero();
    }
}
