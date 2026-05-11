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
package org.os890.jawelte.tests.jta.scenario24;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ServiceLoader;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.jta.api.port.TransactionManagerProvider;

/**
 * Ticket-006 scenario #24 — {@code TransactionManagerProvider.isAvailable()}
 * is fast, side-effect-free, and idempotent. The same call invoked
 * many times must always return the same result, and must not
 * incidentally construct a {@code TransactionManager}.
 */
@EnableTestBeans
public class Scenario24Test {

    /** No-arg constructor for CDI. */
    public Scenario24Test() {
    }

    @Test
    public void isAvailableIsConsistentAcrossRepeatedCalls() {
        // Walk every ServiceLoader-registered provider (by default, the
        // AutoSelectTransactionManagerProvider wrapper that jta-module
        // ships in its own META-INF/services) and verify each one's
        // isAvailable() returns a stable result across repeated calls.
        // The test profile pins exactly one detail impl's classes onto
        // the classpath, but the consistency assertion holds for any
        // shape.
        boolean foundAtLeastOneAvailable = false;
        for (TransactionManagerProvider provider
                : ServiceLoader.load(TransactionManagerProvider.class)) {
            boolean firstCall = provider.isAvailable();
            for (int i = 0; i < 100; i++) {
                assertThat(provider.isAvailable())
                        .as("provider '%s' call %d must return the same value as the first",
                                provider.name(), i + 1)
                        .isEqualTo(firstCall);
            }
            foundAtLeastOneAvailable = foundAtLeastOneAvailable || firstCall;
        }
        assertThat(foundAtLeastOneAvailable)
                .as("at least one TransactionManagerProvider must be available under the test profile")
                .isTrue();
    }
}
