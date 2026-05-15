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

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.os890.jawelte.module.wiremock.api.event.WireMockServersStopped;

/**
 * {@link ApplicationScoped @ApplicationScoped} observer of the
 * {@link WireMockServersStopped} event the lifecycle adapter
 * fires in {@code afterAll}. Increments {@link #FIRED_COUNT}
 * once per fire so {@link Scenario10Test} can assert the event
 * was actually published.
 *
 * <p>Application scope is deliberate: scope-module's
 * {@code @TestClassScoped} context has already been deactivated
 * by the time wiremock-module's {@code afterAll} runs, so a
 * {@code @TestClassScoped} observer would not be reached.
 */
@ApplicationScoped
public class Scenario10StopRecorder {

    /** Number of {@link WireMockServersStopped} events received. */
    public static final AtomicInteger FIRED_COUNT = new AtomicInteger();

    /** No-arg constructor required by the CDI runtime. */
    public Scenario10StopRecorder() {
    }

    void onWireMockStopped(@Observes WireMockServersStopped event) {
        FIRED_COUNT.incrementAndGet();
    }
}
