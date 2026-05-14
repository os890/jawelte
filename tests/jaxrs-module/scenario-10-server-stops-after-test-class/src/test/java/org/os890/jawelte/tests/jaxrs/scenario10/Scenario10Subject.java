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
package org.os890.jawelte.tests.jaxrs.scenario10;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jaxrs.api.EnableJaxRs;
import org.os890.jawelte.module.jaxrs.api.TestUrl;

/**
 * Test-subject class executed by {@link Scenario10Test} via
 * {@code EngineTestKit}. Its single {@code @Test} method captures
 * the live {@link TestUrl} into the {@link #CAPTURED_URL} static
 * field so the outer test can probe that port AFTER the subject's
 * {@code afterAll} has stopped the embedded server.
 *
 * <p>Doesn't follow the {@code *Test} naming so Surefire skips it
 * — it's only ever invoked from {@code EngineTestKit} by the
 * sibling {@code Scenario10Test}.
 */
@EnableTestBeans
@EnableJaxRs(restResources = {Scenario10HelloResource.class})
public class Scenario10Subject {

    private static volatile String capturedUrl;

    @Inject
    private TestUrl testUrl;

    /** Default no-arg constructor used by JUnit. */
    public Scenario10Subject() {
    }

    /**
     * The URL captured during this subject's run; {@code null}
     * before {@link #captureUrl()} executes or after a fresh test
     * run has cleared it.
     *
     * @return the captured URL, or {@code null} when uncaptured
     */
    public static String getCapturedUrl() {
        return capturedUrl;
    }

    /**
     * Clear the captured URL. The outer test calls this before
     * triggering the inner run so a stale value from a previous
     * JVM invocation can't satisfy the assertion.
     */
    public static void clearCapturedUrl() {
        capturedUrl = null;
    }

    /**
     * Read the live URL into the static capture field. The
     * server is up at this point; the assertion that it goes
     * down lives in the outer test class.
     */
    @Test
    public void captureUrl() {
        capturedUrl = testUrl.get();
    }
}
