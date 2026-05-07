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
package org.os890.jawelte.tests.scope.scenario23;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.spi.Contextual;
import jakarta.enterprise.context.spi.CreationalContext;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.module.scope.impl.adapter.context.ScopedBeanInstance;
import org.os890.jawelte.module.scope.impl.adapter.context.TestClassScopeStore;
import org.os890.jawelte.module.scope.impl.adapter.context.TestMethodScopeStore;

/**
 * Scenario 23 — scope-module's stores are parallel-safe across test
 * classes. Two threads simultaneously drive two independent
 * {@code ScopeStore} pairs (one per "test class") through allocate
 * / put / destroyAll cycles; the two pairs end up with disjoint
 * states and never see each other's beans.
 *
 * <p>Note: the integration form of this scenario — two {@code SeContainer}
 * boots on two threads in the same JVM — depends on the underlying
 * CDI runtime's parallel-safety, not scope-module's. That is governed
 * by the project-wide TICKET-003 rule "any non-parallel-safe module
 * on the classpath collapses the test mode to one method in one JVM
 * at a time", and is by design out of this scenario's scope. The
 * direct unit test here pins down the property scope-module is
 * actually responsible for: independent stores, independent state.
 */
class Scenario23Test {

    @Test
    void twoStorePairsRunningInParallelStayIsolated() throws Exception {
        TestClassScopeStore classStoreA = new TestClassScopeStore();
        TestClassScopeStore classStoreB = new TestClassScopeStore();
        TestMethodScopeStore methodStoreA = new TestMethodScopeStore();
        TestMethodScopeStore methodStoreB = new TestMethodScopeStore();
        methodStoreA.allocate();
        methodStoreB.allocate();

        Contextual<Object> sharedKey = identityContextual();
        Object beanA = new Object();
        Object beanB = new Object();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch starter = new CountDownLatch(1);
            CompletableFuture<Void> futureA = CompletableFuture.runAsync(() -> {
                try {
                    starter.await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                classStoreA.map().put(sharedKey, new ScopedBeanInstance<>(beanA, null));
                methodStoreA.map().put(sharedKey, new ScopedBeanInstance<>(beanA, null));
            }, pool);
            CompletableFuture<Void> futureB = CompletableFuture.runAsync(() -> {
                try {
                    starter.await();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                classStoreB.map().put(sharedKey, new ScopedBeanInstance<>(beanB, null));
                methodStoreB.map().put(sharedKey, new ScopedBeanInstance<>(beanB, null));
            }, pool);
            starter.countDown();
            CompletableFuture.allOf(futureA, futureB).get(15, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // Each store pair holds only the bean its own thread put in.
        assertThat(beanFromMap(classStoreA.map(), sharedKey)).isSameAs(beanA);
        assertThat(beanFromMap(classStoreB.map(), sharedKey)).isSameAs(beanB);
        assertThat(beanFromMap(methodStoreA.map(), sharedKey)).isSameAs(beanA);
        assertThat(beanFromMap(methodStoreB.map(), sharedKey)).isSameAs(beanB);

        // Deactivating one pair does not touch the other.
        methodStoreA.destroyAll();
        assertThat(methodStoreA.isAllocated()).isFalse();
        assertThat(methodStoreB.isAllocated()).isTrue();
        assertThat(beanFromMap(methodStoreB.map(), sharedKey)).isSameAs(beanB);
    }

    private static Object beanFromMap(
            Map<Contextual<?>, ScopedBeanInstance<?>> map, Contextual<?> key) {
        return map.get(key).instance();
    }

    private static Contextual<Object> identityContextual() {
        return new Contextual<>() {
            @Override
            public Object create(CreationalContext<Object> creationalContext) {
                return new Object();
            }

            @Override
            public void destroy(Object instance, CreationalContext<Object> creationalContext) {
            }
        };
    }
}
