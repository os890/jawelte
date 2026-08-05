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
package org.os890.jawelte.module.flowassert.api;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.os890.cdi.uml.dynamic.flow.renderer.api.CallFlow;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.module.flowassert.api.port.FlowRecordingPort;

/**
 * What the running test method recorded, for assertions that go
 * beyond comparing a diagram — how many outermost calls happened,
 * whether a specific bean was the entry point of one of them, what
 * the call tree looks like ({@code flow.root()}).
 *
 * <p>Every method resolves the active
 * {@link FlowRecordingPort} through
 * {@link TestContext#loadService(Class)} and is only meaningful
 * inside a test method of a class annotated with
 * {@link EnableFlowAssert}.
 *
 * <p>{@code abstract} plus a private constructor per the project's
 * static-utility class convention.
 */
public abstract class RecordedFlows {

    private static volatile FlowRecordingPort cachedPort;

    private RecordedFlows() {
    }

    /**
     * Every flow recorded since the current test method started, in
     * the order the recorder finished them.
     *
     * @return the recorded flows; never {@code null}, possibly empty
     */
    public static List<CallFlow> all() {
        return port().recordedFlows();
    }

    /**
     * The one flow the test method recorded.
     *
     * @return the single recorded flow; never {@code null}
     * @throws IllegalStateException if no flow or more than one flow
     *         was recorded — the message names the entry points that
     *         were seen, so the fix (a
     *         {@link FlowDiff#forEntryPoint(Class)} assertion, or
     *         comparing the combined diagram instead) is visible from
     *         the failure alone
     */
    public static CallFlow single() {
        List<CallFlow> flows = all();
        if (flows.size() == 1) {
            return flows.get(0);
        }
        throw new IllegalStateException("Expected exactly one recorded flow but found "
                + flows.size() + entryPointsOf(flows)
                + ". Use FlowDiff.forEntryPoint(...) for a single chain, or compare the combined"
                + " diagram via FlowDiff.forRecordedFlows().");
    }

    /**
     * The first flow whose outermost call went into {@code beanClass}.
     *
     * @param beanClass the bean class expected to be the entry point;
     *                  must not be {@code null}
     * @return the first matching flow, or {@link Optional#empty()}
     */
    public static Optional<CallFlow> byEntryPoint(Class<?> beanClass) {
        return allByEntryPoint(beanClass).stream().findFirst();
    }

    /**
     * The first flow whose outermost call went into
     * {@code beanClass#methodName}.
     *
     * @param beanClass  the bean class expected to be the entry point;
     *                   must not be {@code null}
     * @param methodName the method expected to be the entry point;
     *                   must not be {@code null}
     * @return the first matching flow, or {@link Optional#empty()}
     */
    public static Optional<CallFlow> byEntryPoint(Class<?> beanClass, String methodName) {
        for (CallFlow flow : allByEntryPoint(beanClass)) {
            if (methodName.equals(flow.entryMethodName())) {
                return Optional.of(flow);
            }
        }
        return Optional.empty();
    }

    /**
     * Every flow whose outermost call went into {@code beanClass}, in
     * recording order.
     *
     * @param beanClass the bean class expected to be the entry point;
     *                  must not be {@code null}
     * @return the matching flows; never {@code null}, possibly empty
     */
    public static List<CallFlow> allByEntryPoint(Class<?> beanClass) {
        List<CallFlow> matching = new ArrayList<>();
        for (CallFlow flow : all()) {
            if (beanClass.getName().equals(flow.root().beanClassName())) {
                matching.add(flow);
            }
        }
        return List.copyOf(matching);
    }

    /**
     * The recording as one diagram — one block per recorded flow, in
     * the notation named by {@code format}. Handy for printing what
     * was recorded while writing a new expected file.
     *
     * @param format the notation name, e.g. {@code "mermaid"} or
     *               {@code "plantuml"}; must be claimed by a
     *               registered dialect
     * @return the combined diagram; never {@code null}
     * @throws IllegalStateException if no dialect carries that name
     */
    public static String combinedDiagram(String format) {
        return FlowDiff.dialectByName(format).render(all());
    }

    /**
     * Wait until at least {@code expected} flows were recorded. The
     * deterministic handle for a flow produced on another thread — an
     * asynchronous CDI event observer records a flow of its own, and
     * nothing else guarantees it finished before the assertion runs.
     *
     * @param expected the number of flows to wait for
     * @param timeout  how long to wait at most; must not be {@code null}
     * @return {@code true} if the count was reached, {@code false} on
     *         timeout — the caller decides whether that is a failure
     */
    public static boolean awaitFlowCount(int expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (all().size() < expected) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /**
     * Drop what was recorded so far, so an assertion only sees what
     * happens from here on — the way to keep a test's own setup calls
     * out of the compared diagram.
     */
    public static void clear() {
        port().clear();
    }

    static Class<?> testClass() {
        return port().testClass();
    }

    static String testMethodName() {
        return port().testMethodName();
    }

    private static String entryPointsOf(List<CallFlow> flows) {
        if (flows.isEmpty()) {
            return "";
        }
        StringBuilder entryPoints = new StringBuilder(" (");
        for (int i = 0; i < flows.size(); i++) {
            CallFlow flow = flows.get(i);
            entryPoints.append(i == 0 ? "" : ", ")
                    .append(flow.entryTypeSimpleName())
                    .append('.')
                    .append(flow.entryMethodName());
        }
        return entryPoints.append(')').toString();
    }

    private static FlowRecordingPort port() {
        FlowRecordingPort cached = cachedPort;
        if (cached != null) {
            return cached;
        }
        FlowRecordingPort resolved = TestContext.loadService(FlowRecordingPort.class);
        if (resolved == null) {
            throw new IllegalStateException("No " + FlowRecordingPort.class.getName()
                    + " on the classpath - add jawelte-flow-assert-module-impl to the test classpath.");
        }
        cachedPort = resolved;
        return resolved;
    }
}
