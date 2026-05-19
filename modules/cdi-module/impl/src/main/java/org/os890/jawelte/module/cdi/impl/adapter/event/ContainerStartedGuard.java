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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Static flag tracking whether jawelte's {@code ContainerStarted}
 * event has already been fired for the current test class. Used by
 * the managed bootstrap path ({@code CdiTestBeanContainer.beforeAll})
 * and the {@code @QuarkusTest} bridge
 * ({@link ContainerStartedBridgeBean}) to avoid duplicate firings
 * when both paths happen to dispatch the event.
 *
 * <p>Reset between test classes by the managed path's
 * {@code CdiTestBeanContainer.afterAll}; the Quarkus path resets
 * implicitly because ArC re-initialises per test class and the
 * bridge bean's lifecycle resets with it.
 */
public abstract class ContainerStartedGuard {

    private static final AtomicBoolean FIRED = new AtomicBoolean();

    /** Suppressed-instantiation constructor — the class is a pure static holder. */
    protected ContainerStartedGuard() {
    }

    /**
     * Mark the event as having been fired iff it wasn't already.
     *
     * @return {@code true} if the caller is the one who flipped the
     *         flag (and therefore should fire the event);
     *         {@code false} if a prior caller already did
     */
    public static boolean markFiredIfNotYet() {
        return FIRED.compareAndSet(false, true);
    }

    /**
     * Reset the flag so the next test class's bootstrap can fire the
     * event again. Called from
     * {@code CdiTestBeanContainer.afterAll} on the managed path.
     */
    public static void reset() {
        FIRED.set(false);
    }
}
