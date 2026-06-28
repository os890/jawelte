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
package org.os890.jawelte.tests.lnp.scenario10;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.enterprise.inject.spi.ProcessBean;
import jakarta.enterprise.inject.spi.ProcessInjectionPoint;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.cdi.impl.adapter.extension.TestBeansCdiExtension;
import org.os890.jawelte.module.springdata.adapter.extension.SpringDataRepositoryExtension;

/**
 * Load-and-performance stress test (skipped in the normal suite; run via
 * {@code -Plnp}) that drives the CDI lifecycle-observer methods of two
 * extensions exactly as the container would — concurrently, the way Weld's
 * parallel deployer dispatches {@code ProcessInjectionPoint} /
 * {@code ProcessBean} events on multiple threads.
 *
 * <p>Each test pre-creates a distinct event per element, then fires a tight
 * concurrent burst of real observer calls (invoked reflectively because the
 * observer methods are package-private) and asserts the collecting set kept
 * every element. The sets are now {@code ConcurrentHashMap.newKeySet()}, so the
 * count is always exact; reverting a set to a plain {@code HashSet} /
 * {@code LinkedHashSet} loses elements (or throws during a resize race),
 * failing the assertion.
 *
 * <p>Coverage note: this exercises {@code TestBeansCdiExtension}'s
 * {@code ProcessInjectionPoint} observer and {@code SpringDataRepositoryExtension}'s
 * {@code ProcessBean} observer — both extensions, both observer phases. Spring
 * Data's {@code ProcessInjectionPoint} path (discoveredRepositories) needs
 * distinct {@code Repository} sub-interfaces, which can't be synthesised in
 * bulk; it carries the identical {@code newKeySet} fix and the same
 * observer-add shape exercised here for the cdi {@code ProcessInjectionPoint}.
 */
class ConcurrentObserverDispatchTest {

    private static final int THREADS = 8;
    private static final int PER_THREAD = 6_000;
    private static final int TOTAL = THREADS * PER_THREAD;

    @Test
    void cdiProcessInjectionPointObserverToleratesConcurrentDispatch() throws Exception {
        TestBeansCdiExtension extension = new TestBeansCdiExtension();
        Method observer = TestBeansCdiExtension.class
                .getDeclaredMethod("onProcessInjectionPoint", ProcessInjectionPoint.class);
        observer.setAccessible(true);

        List<Object> events = new ArrayList<>(TOTAL);
        for (int id = 0; id < TOTAL; id++) {
            events.add(new FakeProcessInjectionPoint(new FakeInjectionPoint(new UniqueParameterizedType(id))));
        }

        fireConcurrently(events, event -> observer.invoke(extension, event));

        assertThat(readSet(extension, "unsatisfiedCandidateIps"))
                .as("the cdi ProcessInjectionPoint observer must not lose candidates "
                        + "under concurrent (Weld-style) dispatch")
                .hasSize(TOTAL);
    }

    @Test
    void springDataProcessBeanObserverToleratesConcurrentDispatch() throws Exception {
        SpringDataRepositoryExtension extension = new SpringDataRepositoryExtension();
        Method observer = SpringDataRepositoryExtension.class
                .getDeclaredMethod("onProcessBean", ProcessBean.class);
        observer.setAccessible(true);

        List<Object> events = new ArrayList<>(TOTAL);
        for (int id = 0; id < TOTAL; id++) {
            Type uniqueType = new UniqueParameterizedType(id);
            events.add(new FakeProcessBean(new FakeBean(Set.of(uniqueType))));
        }

        fireConcurrently(events, event -> observer.invoke(extension, event));

        assertThat(readSet(extension, "existingBeanTypes"))
                .as("the spring-data ProcessBean observer must not lose bean types "
                        + "under concurrent (Weld-style) dispatch")
                .hasSize(TOTAL);
    }

    private interface EventAction {
        void accept(Object event) throws Exception;
    }

    private static void fireConcurrently(List<Object> events, EventAction action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                int from = t * PER_THREAD;
                int to = from + PER_THREAD;
                Callable<Void> task = () -> {
                    start.await();
                    for (int i = from; i < to; i++) {
                        action.accept(events.get(i));
                    }
                    return null;
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static Set<?> readSet(Object extension, String fieldName) throws Exception {
        Field field = extension.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Set<?>) field.get(extension);
    }
}
