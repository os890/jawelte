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
package org.os890.jawelte.tests.batch.scenario16;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * The batch observer's {@code TimeoutHandler} SPI must be resolved per
 * container — NOT once per JVM in a static field. {@link
 * CountingTimeoutHandler} counts its constructions, which equals the
 * number of {@code loadService(TimeoutHandler.class)} calls.
 *
 * <p>Runs {@link TimeoutHandlerResolutionSubject} in two containers; the
 * handler must be resolved twice (once per container). On the static-field
 * implementation it is resolved once per JVM, so the count would be 1.
 */
class Scenario16Test {

    @Test
    void timeoutHandlerIsResolvedOncePerContainer() {
        CountingTimeoutHandler.CONSTRUCTIONS.set(0);

        runSubject();
        runSubject();

        assertThat(CountingTimeoutHandler.CONSTRUCTIONS.get())
                .as("the TimeoutHandler SPI must be resolved per container (2 boots), "
                        + "not once per JVM")
                .isEqualTo(2);
    }

    private static void runSubject() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(TimeoutHandlerResolutionSubject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1).failed(0));
    }
}
