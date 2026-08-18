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
package org.os890.jawelte.module.cdi.impl.adapter.container;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.Priority;
import jakarta.enterprise.inject.se.SeContainer;
import jakarta.enterprise.inject.se.SeContainerInitializer;
import jakarta.enterprise.inject.spi.CDI;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.cdi.api.port.CdiContainerPort;
import org.os890.jawelte.module.cdi.impl.adapter.extension.TestBeansCdiExtension;

/**
 * Default {@link CdiContainerPort} implementation. Wraps the Jakarta
 * CDI SE bootstrap API ({@link SeContainerInitializer}) with automatic
 * extension discovery left enabled, so the cdi-module's
 * {@link TestBeansCdiExtension} is contributed through CDI's standard
 * {@code ServiceLoader} mechanism (the
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}
 * file shipped in this module) — the same single registration path
 * every other jawelte module uses.
 *
 * <p>The extension is deliberately <em>not</em> also added via
 * {@code addExtensions(...)}: that would register it twice (once
 * programmatically, once via discovery), which some CDI SE
 * implementations de-duplicate and others do not, instantiating the
 * extension twice. Relying on discovery alone is also the only path
 * that works when the container is booted externally — e.g.
 * {@code @EnableTestBeans(manageContainer=false)}, where this port's
 * {@link #start(TestContext)} never runs and the user's own
 * {@code SeContainerInitializer.newInstance().initialize()} discovers
 * the extension from the service file.
 *
 * <p>Annotated {@code @Priority(Integer.MAX_VALUE)} so any
 * user-supplied implementation with a lower priority value
 * automatically wins via the project-wide
 * {@code ServicePriorityResolver}. A future quarkus-module's port
 * will ship at a lower priority and replace this impl seamlessly.
 *
 * <p>Loaded by {@link TestContext#loadService(Class)} from
 * {@code CdiTestBeanContainer.beforeAll(...)} / {@code afterAll(...)}.
 */
@Priority(Integer.MAX_VALUE)
public class SeContainerCdiContainerPort implements CdiContainerPort {

    /** No-arg constructor required by {@code ServiceLoader}. */
    public SeContainerCdiContainerPort() {
    }

    /**
     * The test class whose container failed to start and could not be
     * released, or {@code null} when nothing is outstanding. Read by the
     * next class that fails to start, so its error can name the real
     * cause instead of leaving the reader to work out that
     * "already registered" means "look at an earlier class".
     */
    private static final AtomicReference<String> UNRELEASED_CONTAINER_OWNER = new AtomicReference<>();

    @Override
    public void start(TestContext testContext) {
        // Discovery is left enabled, so TestBeansCdiExtension is picked
        // up from META-INF/services exactly once — no addExtensions(...),
        // which would double-register it (see class javadoc).
        String leakedFrom = UNRELEASED_CONTAINER_OWNER.get();
        SeContainer container;
        try {
            container = SeContainerInitializer.newInstance().initialize();
        } catch (RuntimeException | Error startFailure) {
            // initialize() registered the container against this
            // classloader before it got as far as failing — a deployment
            // error or a throwing @Initialized(ApplicationScoped)
            // observer both land here. The handle is lost, so stop()
            // would find no metadata and close nothing.
            if (releaseWithPortableApiOnly(startFailure)) {
                UNRELEASED_CONTAINER_OWNER.set(null);
            } else if (leakedFrom == null) {
                UNRELEASED_CONTAINER_OWNER.set(testContext.getTestClass().getName());
            }
            if (leakedFrom != null) {
                throw explainCollateralFailure(startFailure, leakedFrom);
            }
            throw startFailure;
        }
        // A successful start proves nothing is outstanding any more.
        UNRELEASED_CONTAINER_OWNER.set(null);
        testContext.bindMetadata(SeContainer.class, container);
    }

    /**
     * Best-effort release of a container whose {@code initialize()}
     * threw, so its registration does not outlive this test class.
     *
     * <p>Portable API only, and measured not to succeed on either runtime
     * the suite uses. CDI gives exactly one handle on a running container
     * — {@link SeContainer} — and no way at all to abandon a bootstrap
     * that threw: {@code initialize()} either returns the handle or loses
     * it. Probed on a failed start:
     *
     * <ul>
     *   <li>OpenWebBeans returns {@code org.apache.webbeans.container.OwbCDI}
     *       — not a {@code SeContainer}, and not {@link AutoCloseable}
     *       either, so there is nothing to call.</li>
     *   <li>Weld throws from {@code CDI.current()} outright, so there is
     *       nothing to inspect.</li>
     * </ul>
     *
     * Both therefore fall through and the caller reports a collateral
     * failure that names the class holding the real error.
     *
     * <p>The attempt is kept because it is the only correct thing the
     * spec offers, costs nothing, and pays off on any runtime that does
     * expose its container this way. Reaching into a specific runtime's
     * internals would close the gap for one implementation and rot on
     * the next — the suite runs on both, with more runtimes planned.
     *
     * @param startFailure the failure being propagated; anything that
     *                     goes wrong here is attached to it as
     *                     suppressed rather than thrown, since it is
     *                     the exception that explains the run
     * @return {@code true} when a container was actually closed
     */
    private static boolean releaseWithPortableApiOnly(Throwable startFailure) {
        try {
            if (CDI.current() instanceof SeContainer partiallyStarted && partiallyStarted.isRunning()) {
                partiallyStarted.close();
                return true;
            }
        } catch (RuntimeException | Error notReachableThatWay) {
            startFailure.addSuppressed(notReachableThatWay);
        }
        return false;
    }

    /**
     * Wrap a start failure that is a consequence of an earlier test
     * class's leaked container, so the message names the situation
     * instead of describing it in the runtime's terms.
     *
     * <p>"{@code ... is already registered}" is true but useless: it
     * says nothing about there being an earlier failure, which class it
     * was in, or that this one is collateral. In a run of N classes
     * after the first failure, N−1 report it, and which of them holds
     * the real error moves with execution order.
     */
    private static RuntimeException explainCollateralFailure(Throwable startFailure, String leakedFrom) {
        return new IllegalStateException(
                "This test class could not start a CDI container because the one started by "
                        + leakedFrom + " was still registered: that class failed during its own"
                        + " startup and this runtime offers no portable way to release a container"
                        + " whose bootstrap threw. The failure in " + leakedFrom
                        + " is the real one — this failure is a consequence of it.",
                startFailure);
    }

    @Override
    public void stop(TestContext testContext) {
        testContext.getMetadata(SeContainer.class).ifPresent(container -> {
            try {
                container.close();
            } finally {
                testContext.unbindMetadata(SeContainer.class);
            }
        });
    }
}
