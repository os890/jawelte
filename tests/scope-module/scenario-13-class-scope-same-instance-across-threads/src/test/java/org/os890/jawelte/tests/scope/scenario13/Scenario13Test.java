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
package org.os890.jawelte.tests.scope.scenario13;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestClassScoped;

@EnableTestBeans
class Scenario13Test {

    @Inject
    Recorder recorder;

    @Test
    void firstMethodFansOutToWorkerThreads() throws Exception {
        runWorkers();
    }

    @Test
    void secondMethodFansOutToWorkerThreads() throws Exception {
        runWorkers();
    }

    @AfterAll
    static void assertOneInstanceForTheWholeClassAcrossEveryThread() {
        // Two methods × 8 worker threads = 16 calls; every call
        // observed the same identityKey (same backing instance).
        // @PostConstruct fired exactly once for the whole class.
        assertThat(Recorder.OBSERVED_IDENTITY_KEYS)
                .as("every worker thread across every method must resolve to the same backing instance")
                .hasSize(1);
        assertThat(Recorder.POST_CONSTRUCT_INVOCATIONS.get())
                .as("@PostConstruct must fire exactly once for the whole class lifetime")
                .isEqualTo(1);
    }

    private void runWorkers() throws Exception {
        int workers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            CountDownLatch starter = new CountDownLatch(1);
            for (int i = 0; i < workers; i++) {
                pool.submit(() -> {
                    starter.await();
                    Recorder.OBSERVED_IDENTITY_KEYS.add(recorder.identityKey());
                    return null;
                });
            }
            starter.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @TestClassScoped
    public static class Recorder {

        static final AtomicInteger POST_CONSTRUCT_INVOCATIONS = new AtomicInteger();
        static final Set<Integer> OBSERVED_IDENTITY_KEYS = ConcurrentHashMap.newKeySet();
        private int identityKey;

        @PostConstruct
        void onPostConstruct() {
            this.identityKey = POST_CONSTRUCT_INVOCATIONS.incrementAndGet();
        }

        public int identityKey() {
            return this.identityKey;
        }
    }
}
