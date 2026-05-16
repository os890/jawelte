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
package org.os890.jawelte.tests.wiremock.scenario10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Scenario 10 — verifies the lifecycle adapter stops every
 * registered {@code WireMockServer} in {@code afterAll}. The
 * subject captures its injected server into
 * {@link Scenario10ServerHolder#SERVER} during the test method;
 * after {@code EngineTestKit} returns the test asserts the
 * captured reference's {@link WireMockServer#isRunning()} is
 * {@code false}.
 *
 * <p>WireMock 3.x's {@code stop()} is synchronous — once it
 * returns, the underlying Jetty lifecycle is finished and
 * {@code isRunning()} flips to {@code false}. No timing race
 * (the previous TCP-probe approach was deferred for exactly that
 * reason).
 */
class Scenario10Test {

    @Test
    void serverIsStoppedAfterTestClass() {
        Scenario10ServerHolder.SERVER.set(null);

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario10Subject.class))
                .execute();

        WireMockServer captured = Scenario10ServerHolder.SERVER.get();
        assertThat(captured)
                .as("the subject's test method captured a WireMockServer reference")
                .isNotNull();
        assertThat(captured.isRunning())
                .as("after the subject's afterAll, the lifecycle adapter has stopped the server")
                .isFalse();
    }
}
