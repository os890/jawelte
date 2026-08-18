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
package org.os890.jawelte.tests.resource.scenario03;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

/**
 * That an unresolvable name fails is not the interesting part - a null
 * field "fails" too, eventually, somewhere else. What this asserts is
 * that the failure arrives where the mistake is and says what it is:
 * the field, the name it asked for, and the most likely reason.
 *
 * <p>The name in {@link TypoRepository} is one character off the
 * declared one, which is the realistic version of this mistake and the
 * one a good message turns into a five-second fix.
 */
class Scenario03Test {

    @Test
    void theFailureNamesTheFieldAndTheName() {
        List<Throwable> failures = Failures.of(EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(UnresolvableSubject.class))
                .execute());

        assertThat(failures)
                .as("an unresolvable @Resource has to fail the run, not leave a null behind")
                .isNotEmpty();

        String chain = failures.stream().map(Failures::messageChain).reduce("", String::concat);
        assertThat(chain)
                .as("the name that was asked for")
                .contains("java:app/jdbc/AppDSS");
        assertThat(chain)
                .as("the field it was asked for from, so the reader does not have to search")
                .contains(TypoRepository.class.getName() + ".declared");
        assertThat(chain)
                .as("the most likely cause, spelled out")
                .contains("nothing is bound under that name");
    }
}
