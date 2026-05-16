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

import java.util.concurrent.atomic.AtomicReference;

import com.github.tomakehurst.wiremock.WireMockServer;

/**
 * Cross-context communication channel for scenario 10. The
 * subject's {@code @Test} method writes its injected
 * {@link WireMockServer} reference into {@link #SERVER};
 * {@link Scenario10Test} reads it back after
 * {@code EngineTestKit} returns and asserts the server has been
 * stopped (<code>isRunning() == false</code>).
 */
public class Scenario10ServerHolder {

    /**
     * Captured reference to the {@link WireMockServer} that was
     * injected into the subject during its test method. Written
     * once by {@link Scenario10Subject}; read after the engine
     * returns by {@link Scenario10Test}.
     */
    public static final AtomicReference<WireMockServer> SERVER = new AtomicReference<>();

    /** No-arg constructor — utility holder. */
    protected Scenario10ServerHolder() {
    }
}
