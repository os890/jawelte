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
package org.os890.jawelte.tests.scope.scenario31;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.event.ContainerStarted;
import org.os890.jawelte.module.scope.api.TestClassScoped;

/**
 * Scenario 31 — a {@code @TestClassScoped} bean observes
 * {@link ContainerStarted} and behaves as a per-test-class
 * eager-init hook. {@code ContainerStarted} is fired exactly
 * once by {@code CdiTestBeanContainer.beforeAll} (after the
 * container is up, before the module-port chain runs), so a
 * {@code @TestClassScoped} observer fires once per test class.
 *
 * <p>The {@link TestClassScopedContext} registered by
 * scope-module's CDI Extension is active from CDI bootstrap
 * onwards (its store is allocated in the context's constructor
 * during {@code AfterBeanDiscovery}), so when
 * {@code ContainerStarted} fires the observer can resolve and
 * instantiate the {@code @TestClassScoped} bean lazily; the
 * same instance is then visible across every test method via
 * the class-scoped context.
 *
 * <p>Verified by firing two test methods against the same bean
 * and asserting that the observer counter is {@code 1} from
 * both methods — the event was delivered exactly once, and
 * both methods saw the same instance via the proxied
 * {@code @TestClassScoped} bean.
 */
@EnableTestBeans
class Scenario31Test {

    @Inject
    EagerInitBean bean;

    @Test
    void firstMethodSeesContainerStartedObserved() {
        assertThat(bean.observedContainerStartedCount())
                .as("observer fired exactly once before the first test method runs")
                .isEqualTo(1);
        assertThat(bean.observedFor())
                .as("event payload carries the active test class")
                .isEqualTo(Scenario31Test.class);
    }

    @Test
    void secondMethodSeesSameInstanceAndCountUnchanged() {
        assertThat(bean.observedContainerStartedCount())
                .as("count stays at 1 across methods — the @TestClassScoped instance is shared")
                .isEqualTo(1);
    }

    @TestClassScoped
    public static class EagerInitBean {

        private final AtomicInteger observedCount = new AtomicInteger();
        private volatile Class<?> observedFor;

        void onContainerStarted(@Observes ContainerStarted event) {
            observedCount.incrementAndGet();
            observedFor = event.getTestClass();
        }

        public int observedContainerStartedCount() {
            return observedCount.get();
        }

        public Class<?> observedFor() {
            return observedFor;
        }
    }
}
