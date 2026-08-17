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
package org.os890.jawelte.tests.datasource.scenario07;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * The end of the lifecycle, which a test cannot assert about its own
 * class: by the time a {@code @Test} method runs, {@code afterAll} has
 * not happened yet, and by the time it has, there is no test left to
 * assert in.
 *
 * <p>So both subjects run through {@code EngineTestKit} — a complete
 * jawelte lifecycle each, boot to shutdown — and the assertions happen
 * here, afterwards, against what {@link RecordingDataSource} recorded.
 */
class Scenario07Test {

    @BeforeEach
    void forgetPreviousRuns() {
        RecordingDataSource.reset();
    }

    @Test
    void theDeclaredDataSourceIsClosedWhenTheTestClassIsDone() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(FirstSubject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(RecordingDataSource.created())
                .as("exactly one data source is declared, so exactly one is built")
                .hasSize(1);
        assertThat(RecordingDataSource.closed())
                .as("afterAll has to close what beforeAll opened")
                .containsExactlyElementsOf(RecordingDataSource.created());
    }

    @Test
    void theJndiEntryIsRemovedWhenTheTestClassIsDone() throws NamingException {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(FirstSubject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        InitialContext context = new InitialContext();
        assertThatThrownBy(() -> context.lookup("java:comp/env/jdbc/FirstDS"))
                .as("a stale binding would let the next test class resolve a closed data source")
                .isInstanceOf(NamingException.class);
    }

    @Test
    void aSecondTestClassGetsItsOwnInstance() {
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(FirstSubject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));
        EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(SecondSubject.class))
                .execute()
                .testEvents()
                .assertStatistics(stats -> stats.started(1).succeeded(1));

        assertThat(RecordingDataSource.created())
                .as("each test class declares its own data source and gets its own instance")
                .hasSize(2);
        assertThat(RecordingDataSource.created().get(0))
                .isNotSameAs(RecordingDataSource.created().get(1));
        assertThat(RecordingDataSource.closed())
                .as("both classes have finished, so both instances are closed")
                .hasSize(2);
    }

    @Test
    void runningTheSameClassTwiceBuildsAFreshInstanceEachTime() {
        for (int run = 0; run < 2; run++) {
            EngineTestKit.engine("junit-jupiter")
                    .selectors(selectClass(FirstSubject.class))
                    .execute()
                    .testEvents()
                    .assertStatistics(stats -> stats.started(1).succeeded(1));
        }

        assertThat(RecordingDataSource.created())
                .as("nothing is carried over between runs of a class — no cached data source, "
                        + "no leftover registry entry")
                .hasSize(2);
        assertThat(RecordingDataSource.closed()).hasSize(2);
    }
}
