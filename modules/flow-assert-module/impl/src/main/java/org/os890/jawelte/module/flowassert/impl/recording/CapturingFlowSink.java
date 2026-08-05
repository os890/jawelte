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

import org.os890.cdi.uml.dynamic.flow.renderer.api.CallFlow;
import org.os890.cdi.uml.dynamic.flow.renderer.api.FlowSink;

/**
 * Hands every flow the recorder finishes to the
 * {@link RecordedFlowStore}.
 *
 * <p>Registered statically via {@code FlowSinks.register(...)} by the
 * lifecycle adapter rather than as a CDI bean: the registry is
 * consulted per finished flow, so registering after the container
 * booted loses nothing, and a static sink behaves identically on
 * OpenWebBeans and on Weld without depending on how either discovers
 * beans of a foreign jar.
 *
 * <p>The recorder suspends itself while publishing, so nothing this
 * sink calls is recorded in turn.
 */
public class CapturingFlowSink implements FlowSink {

    /** The one instance the lifecycle adapter registers and unregisters. */
    public static final CapturingFlowSink INSTANCE = new CapturingFlowSink();

    private CapturingFlowSink() {
    }

    @Override
    public void onFlowRecorded(CallFlow flow) {
        RecordedFlowStore.add(flow);
    }
}
