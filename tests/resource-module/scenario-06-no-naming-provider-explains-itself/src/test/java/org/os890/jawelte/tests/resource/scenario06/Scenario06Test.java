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
package org.os890.jawelte.tests.resource.scenario06;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * This module's archive declares no naming provider, so
 * jndi-module hands out no writable root and there is nothing to
 * resolve a name against.
 *
 * <p>datasource-module treats that as a documented no-op - injection
 * does not go through naming, so a suite that never looks anything up
 * is unaffected. Here it cannot be: filling the field <em>is</em> the
 * job, and the only alternatives are an actionable failure or a null
 * that surfaces somewhere else entirely. The message therefore has to
 * name what is missing and what to do about it.
 */
class Scenario06Test {

    @Test
    void theFailureSaysWhatIsMissing() {
        List<Throwable> failures = Failures.of(EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(NoNamingSubject.class))
                .execute());

        assertThat(failures)
                .as("a @Resource that cannot be filled must not pass silently")
                .isNotEmpty();

        String chain = failures.stream().map(Failures::messageChain).reduce("", String::concat);
        assertThat(chain)
                .as("what is missing")
                .contains("no JNDI naming provider is installed");
        assertThat(chain)
                .as("and what to do about it")
                .contains("xbean-naming");
    }
}
