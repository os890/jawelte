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
package org.os890.jawelte.module.cdi.impl.adapter.event;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;

import org.os890.jawelte.core.api.event.ContainerStarted;

/**
 * Bridges Quarkus's container-startup event to jawelte's
 * {@link ContainerStarted} event under {@code @QuarkusTest}, where
 * {@code CdiTestBeanContainer.beforeAll} is skipped (Quarkus owns the
 * boot).
 *
 * <p>Observes the standard CDI
 * {@code @Initialized(ApplicationScoped.class)} event — fired by every
 * compliant CDI container once the application scope is active — and
 * re-fires {@link ContainerStarted} from inside the Quarkus runtime
 * classloader.
 *
 * <p>{@link ContainerStartedGuard#markFiredIfNotYet()} guards
 * against double-firing on the managed path: when jawelte's
 * {@code CdiTestBeanContainer.beforeAll} fires {@link ContainerStarted}
 * itself, it sets the flag, and this observer's subsequent firing
 * (if ArC also dispatches {@code @Initialized(ApplicationScoped)} on
 * its own bootstrap) becomes a no-op.
 */
@ApplicationScoped
public class ContainerStartedBridgeBean {

    /** Public no-arg constructor required by CDI. */
    public ContainerStartedBridgeBean() {
    }

    /**
     * CDI observer for the application-scope-initialised event. Fires
     * {@link ContainerStarted} unless
     * {@link ContainerStartedGuard#markFiredIfNotYet()} reports the
     * managed path has already done so.
     *
     * @param event   the standard CDI initialisation marker (unused;
     *                we only care about the trigger)
     * @param emitter CDI event producer used to dispatch the
     *                {@link ContainerStarted} event
     */
    public void onApplicationScopeInitialized(
            @Observes @Initialized(ApplicationScoped.class) Object event,
            Event<ContainerStarted> emitter) {
        if (ContainerStartedGuard.markFiredIfNotYet()) {
            emitter.fire(new ContainerStarted(resolveTestClass()));
        }
    }

    /**
     * Resolve the running test class for the {@link ContainerStarted}
     * event. Under {@code @QuarkusTest} the bridge bean runs inside
     * Quarkus's runtime classloader and doesn't share state with
     * jawelte's {@code DelegatingJUnitExtension} (different
     * classloader, different statics). The extension publishes the
     * test class FQN as a JVM-wide system property
     * ({@link #CURRENT_TEST_CLASS_PROPERTY}); we read it back here
     * and resolve the class via the thread context classloader.
     * Returns {@code null} when the property is unset or the class
     * is unloadable — observers that don't care about the payload
     * still receive the event.
     */
    private static Class<?> resolveTestClass() {
        String fqn = System.getProperty(CURRENT_TEST_CLASS_PROPERTY);
        if (fqn == null || fqn.isEmpty()) {
            return null;
        }
        try {
            return Class.forName(fqn, false,
                    Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * JVM-wide system property carrying the FQN of the currently
     * running test class. Set by
     * {@code DelegatingJUnitExtension.beforeAll} and cleared in
     * {@code afterAll}. Shared via system properties because
     * jawelte's JUnit extension classloader and the Quarkus runtime
     * classloader are different, so static / ThreadLocal handoff
     * doesn't work here.
     */
    public static final String CURRENT_TEST_CLASS_PROPERTY =
            "org.os890.jawelte.cdi.bridge.current-test-class";
}
