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
package org.os890.jawelte.module.flowassert.api.port;

import java.util.List;

import org.os890.cdi.uml.dynamic.flow.renderer.api.CallFlow;

/**
 * What the running test method recorded. The driven port between the
 * user-facing api — {@code FlowDiff}, {@code RecordedFlows} — and the
 * sink that collects the recorder's output in flow-assert-module/impl.
 *
 * <p>Resolved through
 * {@code TestContext.loadService(FlowRecordingPort.class)}, so the
 * capture mechanism is swappable by priority, and the api jar stays
 * free of any static recording state of its own.
 *
 * <p>Everything here is scoped to the current test method: the
 * lifecycle adapter clears the recording before the method runs, so
 * {@link #recordedFlows()} never leaks a flow of the previous one.
 *
 * <p>Implementations must be thread-safe: a flow is published on the
 * thread that finished the outermost call, which for an asynchronous
 * CDI event is not the test thread.
 */
public interface FlowRecordingPort {

    /**
     * Every flow recorded since the current test method started, in
     * the order the recorder finished them.
     *
     * @return the recorded flows; never {@code null}, possibly empty
     */
    List<CallFlow> recordedFlows();

    /**
     * Drop what was recorded so far. Called by the lifecycle adapter
     * before each test method, and available to a test that wants to
     * leave its own setup calls out of the assertion.
     */
    void clear();

    /**
     * The test class currently running, as captured when the test
     * method started — the {@code TestContext} itself is only
     * reachable during the container bootstrap window.
     *
     * @return the test class, or {@code null} outside a test method
     */
    Class<?> testClass();

    /**
     * The name of the test method currently running.
     *
     * @return the method name, or {@code null} outside a test method
     */
    String testMethodName();
}
