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
package org.os890.jawelte.tests.cdi.scenario64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;

class Scenario64Test {

    @Test
    void injectingOneUnsatisfiedTypeIntoBothABeanAndTheTestClassFailsTheDeployment() {
        List<Event> failures = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(CollidingSubject.class))
                .execute()
                .allEvents()
                .failed()
                .stream()
                .toList();

        assertThat(failures)
                .as("the collision is a deployment failure, so it surfaces before any test method runs")
                .isNotEmpty();

        assertThat(messagesOf(failures))
                .as("both CDI runtimes name the ambiguous type - OpenWebBeans reports "
                        + "AmbiguousResolutionException, Weld reports WELD-001409")
                .contains("AuditService");
    }

    private static String messagesOf(List<Event> failures) {
        StringBuilder collected = new StringBuilder();
        for (Event failure : failures) {
            failure.getPayload(org.junit.platform.engine.TestExecutionResult.class)
                    .flatMap(result -> result.getThrowable())
                    .ifPresent(throwable -> {
                        for (Throwable cursor = throwable; cursor != null; cursor = cursor.getCause()) {
                            collected.append(cursor).append('\n');
                        }
                    });
        }
        return collected.toString();
    }
}
