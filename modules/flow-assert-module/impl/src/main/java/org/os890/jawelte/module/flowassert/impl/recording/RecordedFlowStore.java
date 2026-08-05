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
package org.os890.jawelte.module.flowassert.impl.recording;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.os890.cdi.uml.dynamic.flow.renderer.api.CallFlow;

/**
 * What the running test method recorded, and which method that is.
 *
 * <p>Static on purpose. The recorder publishes a finished flow on
 * whichever thread completed the outermost call, and the assertion
 * reads it from the test thread — long after the container bootstrap
 * window in which {@code TestContext} is reachable has closed. A
 * static store is how the other modules bridge that same gap.
 *
 * <p>{@code CopyOnWriteArrayList} rather than synchronisation: writes
 * happen once per finished flow, reads once per assertion, and an
 * asynchronous observer's flow may land while the test thread is
 * already iterating.
 *
 * <p>{@code abstract} plus a private constructor per the project's
 * static-utility class convention.
 */
public abstract class RecordedFlowStore {

    private static final List<CallFlow> FLOWS = new CopyOnWriteArrayList<>();

    private static volatile Class<?> testClass;

    private static volatile String testMethodName;

    private RecordedFlowStore() {
    }

    /**
     * Take note of a finished flow.
     *
     * @param flow the recorded flow; must not be {@code null}
     */
    public static void add(CallFlow flow) {
        FLOWS.add(flow);
    }

    /**
     * The flows recorded since the last {@link #clear()}.
     *
     * @return the flows in recording order; never {@code null}
     */
    public static List<CallFlow> flows() {
        return List.copyOf(FLOWS);
    }

    /**
     * Drop what was recorded so far, keeping the current test method.
     */
    public static void clear() {
        FLOWS.clear();
    }

    /**
     * Bind the test method the following recordings belong to.
     *
     * @param newTestClass      the test class; may be {@code null} to unbind
     * @param newTestMethodName the test method name; may be {@code null}
     */
    public static void bind(Class<?> newTestClass, String newTestMethodName) {
        testClass = newTestClass;
        testMethodName = newTestMethodName;
    }

    /**
     * The test class the current recording belongs to.
     *
     * @return the test class, or {@code null} outside a test method
     */
    public static Class<?> testClass() {
        return testClass;
    }

    /**
     * The test method the current recording belongs to.
     *
     * @return the method name, or {@code null} outside a test method
     */
    public static String testMethodName() {
        return testMethodName;
    }
}
