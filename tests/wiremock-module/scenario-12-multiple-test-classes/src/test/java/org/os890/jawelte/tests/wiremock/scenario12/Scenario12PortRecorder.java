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
package org.os890.jawelte.tests.wiremock.scenario12;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cross-subject communication channel for scenario 12.
 * {@link Scenario12SubjectA} writes its server port to
 * {@link #PORT_A}; {@link Scenario12SubjectB} writes its port to
 * {@link #PORT_B}. {@link Scenario12Test} runs both subjects via
 * {@code EngineTestKit} and reads the recorded values to assert
 * independence.
 */
public class Scenario12PortRecorder {

    /** Port recorded by {@link Scenario12SubjectA}. */
    public static final AtomicInteger PORT_A = new AtomicInteger();

    /** Port recorded by {@link Scenario12SubjectB}. */
    public static final AtomicInteger PORT_B = new AtomicInteger();

    /** No-arg constructor — utility class, not instantiated by callers. */
    protected Scenario12PortRecorder() {
    }
}
