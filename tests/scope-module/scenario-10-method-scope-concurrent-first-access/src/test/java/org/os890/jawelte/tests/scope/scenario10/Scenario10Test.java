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
package org.os890.jawelte.tests.scope.scenario10;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestMethodScoped;

@EnableTestBeans
class Scenario10Test {

    @Inject
    LazyBean bean;

    @Test
    void concurrentFirstAccessYieldsExactlyOneInstance() throws Exception {
        int workers = 16;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            CountDownLatch starter = new CountDownLatch(1);
            for (int i = 0; i < workers; i++) {
                pool.submit(() -> {
                    starter.await();
                    bean.touch();
                    return null;
                });
            }
            starter.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        // Backed by ConcurrentHashMap.computeIfAbsent in
        // TestMethodScopedContext.get(...) - the create function runs
        // exactly once even under simultaneous first access from
        // many threads.
        assertThat(LazyBean.POST_CONSTRUCT_INVOCATIONS.get())
                .as("@PostConstruct must fire exactly once under concurrent first access")
                .isEqualTo(1);
    }

    @TestMethodScoped
    public static class LazyBean {

        static final AtomicInteger POST_CONSTRUCT_INVOCATIONS = new AtomicInteger();

        @PostConstruct
        void onPostConstruct() {
            POST_CONSTRUCT_INVOCATIONS.incrementAndGet();
        }

        public void touch() {
        }
    }
}
