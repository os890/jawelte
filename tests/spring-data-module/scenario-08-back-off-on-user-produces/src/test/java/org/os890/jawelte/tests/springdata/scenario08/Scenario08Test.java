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
package org.os890.jawelte.tests.springdata.scenario08;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;

/**
 * Scenario 8 — when a user supplies their own
 * {@code @Produces CustomerRepository}, the extension's back-off
 * check detects the existing bean and declines to register its own
 * synthetic. The user's producer wins.
 *
 * <p>The injected repository is the user producer's no-op JDK
 * proxy. Calling {@code count()} records the method name on
 * {@link UserOwnedRepositoryProducer#LAST_METHOD_CALLED} and
 * returns {@code 0L} without touching the database — proving the
 * proxy is the user's, not a Spring Data implementation.
 */
@EnableTestBeans
public class Scenario08Test {

    @Inject
    private CustomerRepository customerRepository;

    /** No-arg constructor for CDI. */
    public Scenario08Test() {
    }

    /** Reset the recorder before each test. */
    @BeforeEach
    public void clearRecorder() {
        UserOwnedRepositoryProducer.LAST_METHOD_CALLED.set(null);
    }

    /** The user's producer serves the injection — invocations route through its handler. */
    @Test
    public void userProducerWinsOverExtension() {
        long count = customerRepository.count();
        assertThat(count)
                .as("user producer returns 0 for primitive long without touching the database")
                .isZero();
        assertThat(UserOwnedRepositoryProducer.LAST_METHOD_CALLED.get())
                .as("user producer's InvocationHandler recorded the count() call — the user's bean won")
                .isEqualTo("count");
    }
}
