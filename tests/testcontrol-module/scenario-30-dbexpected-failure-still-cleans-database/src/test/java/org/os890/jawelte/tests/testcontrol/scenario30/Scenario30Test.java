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
package org.os890.jawelte.tests.testcontrol.scenario30;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

/**
 * Scenario 30 — contract guard: a {@code dbExpected} verify failure on
 * the transactional path must NOT skip jpa-module's inter-method table
 * cleanup.
 *
 * <p>The verify failure surfaces as an {@link AssertionError} (an
 * {@link Error}) from {@code DbDiff.assertEquals()}, raised inside the
 * synchronous {@code AfterTestTransaction} observer that jpa-module
 * fires from its {@code afterEach}. jpa-module's {@code afterEach}
 * aggregates the failure as a {@link Throwable}, still runs its table
 * cleanup and EM-stack drain, and rethrows the {@code AssertionError}
 * unchanged — so the failing method fails (on the assertion) yet leaves
 * a clean database for the next method.
 *
 * <p>Note: under the bundled CDI implementations (OpenWebBeans, Weld)
 * the container wraps the observer's {@code AssertionError} in
 * {@code jakarta.enterprise.event.ObserverException} (a
 * {@code RuntimeException}), so the cleanup already ran before
 * jpa-module's {@code afterEach} was widened to catch {@link Throwable};
 * this scenario therefore locks the end-to-end "verify failure → clean
 * database" guarantee as a regression guard rather than reproducing a
 * live defect.
 *
 * <p>Drives {@link DbExpectedFailureSubject} through
 * {@code EngineTestKit}: method 1 fails with the verify
 * {@code AssertionError}; method 2 asserts the table is empty. Expected
 * outcome: exactly one failure (method 1) and one success (method 2).
 */
class Scenario30Test {

    @Test
    void dbExpectedAssertionFailureStillRunsTableCleanup() {
        Events tests = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(DbExpectedFailureSubject.class))
                .execute()
                .testEvents();

        tests.assertStatistics(stats -> stats.started(2).succeeded(1).failed(1));

        List<String> succeeded = tests.finished().stream()
                .filter(event -> event.getPayload(TestExecutionResult.class)
                        .map(result -> result.getStatus() == TestExecutionResult.Status.SUCCESSFUL)
                        .orElse(false))
                .map(event -> event.getTestDescriptor().getDisplayName())
                .toList();

        assertThat(succeeded)
                .as("the next method must see a clean table; jpa-module's cleanup must run "
                        + "even though the previous method's dbExpected verify threw an AssertionError")
                .containsExactly("nextMethodMustSeeACleanTableDespiteThePreviousVerifyFailure()");

        boolean firstFailedWithAssertionError = tests.finished().stream()
                .map(event -> event.getPayload(TestExecutionResult.class).orElse(null))
                .filter(result -> result != null && result.getStatus() == TestExecutionResult.Status.FAILED)
                .anyMatch(result -> hasAssertionErrorInChain(result.getThrowable().orElse(null)));

        assertThat(firstFailedWithAssertionError)
                .as("method 1 must fail with the dbExpected verify AssertionError in its chain")
                .isTrue();
    }

    private static boolean hasAssertionErrorInChain(Throwable throwable) {
        for (Throwable cursor = throwable; cursor != null; cursor = cursor.getCause()) {
            if (cursor instanceof AssertionError) {
                return true;
            }
        }
        return false;
    }
}
