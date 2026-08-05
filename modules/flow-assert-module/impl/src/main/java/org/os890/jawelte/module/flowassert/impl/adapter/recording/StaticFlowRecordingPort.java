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
package org.os890.jawelte.module.flowassert.impl.adapter.recording;

import java.util.List;

import jakarta.annotation.Priority;

import org.os890.cdi.uml.dynamic.flow.renderer.api.CallFlow;
import org.os890.jawelte.module.flowassert.api.port.FlowRecordingPort;
import org.os890.jawelte.module.flowassert.impl.recording.RecordedFlowStore;

/**
 * Reads the recording of the current test method out of the
 * {@link RecordedFlowStore} — the adapter behind
 * {@code RecordedFlows} and {@code FlowDiff}.
 *
 * <p>Registered at {@code @Priority(Integer.MAX_VALUE)}: a consumer
 * that captures flows differently (a sink of its own, a recording that
 * spans more than one test method) ships an implementation with a
 * lower numeric priority and takes over without touching the api.
 */
@Priority(Integer.MAX_VALUE)
public class StaticFlowRecordingPort implements FlowRecordingPort {

    /** No-arg constructor required by SPI {@code ServiceLoader} lookup. */
    public StaticFlowRecordingPort() {
    }

    @Override
    public List<CallFlow> recordedFlows() {
        return RecordedFlowStore.flows();
    }

    @Override
    public void clear() {
        RecordedFlowStore.clear();
    }

    @Override
    public Class<?> testClass() {
        return RecordedFlowStore.testClass();
    }

    @Override
    public String testMethodName() {
        return RecordedFlowStore.testMethodName();
    }
}
