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
package org.os890.jawelte.tests.jpa.scenario62;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * Drives {@link Scenario62Subject} via JUnit Platform Test Kit and
 * inspects the {@link AfterTestTxRecorder}'s captured events to
 * verify the {@link org.os890.jawelte.core.api.event.AfterTestTransaction}
 * payload contract:
 *
 * <ul>
 *   <li>{@code isCommitted()} = {@code true} when the test method
 *       body completed normally (the wrapping {@code @Transactional}
 *       committed) and {@code false} when the body threw (the
 *       wrapping rolled back).</li>
 *   <li>{@code getTestMethodName()} = the actual {@code @Test}
 *       method's simple name, NOT the test class's simple name.</li>
 * </ul>
 *
 * <p>Pre-§5.1 the lifecycle adapter hardcoded {@code committed=true}
 * and passed the test class name in the method-name slot — observers
 * couldn't tell pass from rollback and saw the wrong identifier.
 * Closes punch-list §5.1.
 *
 * <p>The outer test uses {@code EngineTestKit} so the subject's
 * deliberately throwing method doesn't fail the outer build:
 * {@code testEvents().assertStatistics(stats -&gt; stats.failed(1))}
 * captures the expected failure inside the kit.
 */
public class Scenario62Test {

    /** No-arg constructor required by JUnit. */
    public Scenario62Test() {
    }

    /** AfterTestTransaction payload reflects pass / rollback + actual method name. */
    @Test
    public void afterTestTransactionPayloadCarriesCommittedFlagAndMethodName() {
        AfterTestTxRecorder.EVENTS.clear();

        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(Scenario62Subject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(2).succeeded(1).failed(1));

        assertThat(AfterTestTxRecorder.EVENTS)
                .as("one AfterTestTransaction per @Transactional @Test method run by the kit")
                .hasSize(2);

        assertThat(AfterTestTxRecorder.EVENTS.get(0))
                .as("first event — pass case (aPassingTransactional): committed=true, name=method name")
                .isEqualTo(new AfterTestTxRecorder.RecordedEvent(true, "aPassingTransactional"));

        assertThat(AfterTestTxRecorder.EVENTS.get(1))
                .as("second event — rollback case (bThrowingTransactional): committed=false, "
                        + "name=method name. Pre-§5.1 the committed flag was hardcoded true and "
                        + "the name slot held the test class's simple name.")
                .isEqualTo(new AfterTestTxRecorder.RecordedEvent(false, "bThrowingTransactional"));
    }
}
