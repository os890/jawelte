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
package org.os890.jawelte.tests.wiremock.scenario17;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cross-subject communication channel for scenario 17. The
 * {@code @TestClassScoped} observer in {@link Scenario17Subject}
 * writes its {@code @PreDestroy} probe result here;
 * {@link Scenario17Test} reads it after the engine returns.
 */
public class Scenario17Recorder {

    /**
     * {@code true} when the {@code @PreDestroy} observer saw
     * its captured {@code WireMockServer} reference still
     * {@code isRunning()} — i.e. wiremock-module's afterAll
     * hadn't yet stopped the server.
     */
    public static final AtomicBoolean SERVER_RUNNING_AT_PRE_DESTROY = new AtomicBoolean();

    /**
     * {@code true} when the {@code @PreDestroy} observer actually
     * ran at all (sanity check that scope-module deactivation
     * fired the destroy callback).
     */
    public static final AtomicBoolean PRE_DESTROY_INVOKED = new AtomicBoolean();

    /** No-arg constructor — utility holder. */
    protected Scenario17Recorder() {
    }
}
