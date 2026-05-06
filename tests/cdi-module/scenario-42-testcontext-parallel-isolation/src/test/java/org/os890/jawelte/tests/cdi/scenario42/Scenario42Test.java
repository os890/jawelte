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
package org.os890.jawelte.tests.cdi.scenario42;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;

class Scenario42Test {

    @Test
    void parallelTestClassesEachSeeOwnTestContextOnTheirRunningThread() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);
            AtomicReference<Long> threadAId = new AtomicReference<>();
            AtomicReference<Long> threadBId = new AtomicReference<>();

            executor.submit(() -> runSubject(Scenario42SubjectA.class, threadAId, start, done));
            executor.submit(() -> runSubject(Scenario42SubjectB.class, threadBId, start, done));

            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();

            assertThat(CapturingExtension.SEEN_PER_THREAD.get(threadAId.get()))
                    .isEqualTo(Scenario42SubjectA.class);
            assertThat(CapturingExtension.SEEN_PER_THREAD.get(threadBId.get()))
                    .isEqualTo(Scenario42SubjectB.class);
        } finally {
            executor.shutdownNow();
        }
    }

    private static void runSubject(
            Class<?> subjectClass,
            AtomicReference<Long> threadIdSink,
            CountDownLatch start,
            CountDownLatch done) {
        try {
            threadIdSink.set(Thread.currentThread().threadId());
            start.await();
            EngineTestKit.engine("junit-jupiter").selectors(selectClass(subjectClass)).execute();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            done.countDown();
        }
    }
}
