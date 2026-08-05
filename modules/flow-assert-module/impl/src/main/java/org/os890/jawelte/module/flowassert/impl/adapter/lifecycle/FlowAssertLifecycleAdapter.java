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
package org.os890.jawelte.module.flowassert.impl.adapter.lifecycle;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.Priority;

import org.os890.cdi.uml.dynamic.flow.renderer.api.FlowLabel;
import org.os890.cdi.uml.dynamic.flow.renderer.api.FlowSinks;
import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;
import org.os890.jawelte.module.flowassert.api.EnableFlowAssert;
import org.os890.jawelte.module.flowassert.api.ExpectedFlow;
import org.os890.jawelte.module.flowassert.api.FlowAssertConfig;
import org.os890.jawelte.module.flowassert.api.FlowDiff;
import org.os890.jawelte.module.flowassert.impl.adapter.config.FlowRecordingSettings;
import org.os890.jawelte.module.flowassert.impl.recording.CapturingFlowSink;
import org.os890.jawelte.module.flowassert.impl.recording.RecordedFlowStore;

/**
 * Scopes the recording to the running test method and evaluates
 * {@link ExpectedFlow}.
 *
 * <p>{@code @Priority(Integer.MAX_VALUE)} is what puts it in the right
 * place twice over: the lifecycle ports run in ascending priority
 * order in {@code beforeEach} and in reverse in {@code afterEach}, so
 * this adapter clears the recording <em>last</em> before the test body
 * — after every other module has done its setup, none of which then
 * shows up in the diagram — and asserts <em>first</em> afterwards,
 * before a transaction is rolled back or a container is torn down.
 *
 * <p>Everything is guarded by {@link EnableFlowAssert} on the test
 * class: without it the recorder is switched off anyway and this
 * adapter does nothing at all.
 */
@Priority(Integer.MAX_VALUE)
public class FlowAssertLifecycleAdapter implements TestModuleLifecyclePort {

    /** No-arg constructor required by SPI {@code ServiceLoader} lookup. */
    public FlowAssertLifecycleAdapter() {
    }

    @Override
    public void beforeAll(TestContext testContext) {
        if (!isRecording(testContext)) {
            return;
        }
        // the sink registry is consulted per finished flow, so registering after the
        // container booted is early enough for everything a test method triggers
        FlowSinks.register(CapturingFlowSink.INSTANCE);
    }

    @Override
    public void beforeEach(TestContext testContext) {
        if (!isRecording(testContext)) {
            return;
        }
        String methodName = methodNameOf(testContext).orElse(null);
        RecordedFlowStore.bind(testContext.getTestClass(), methodName);
        RecordedFlowStore.clear();
        FlowLabel.set(testContext.getTestClass().getSimpleName()
                + (methodName == null ? "" : "#" + methodName));
    }

    @Override
    public void afterEach(TestContext testContext) {
        if (!isRecording(testContext)) {
            return;
        }
        try {
            assertExpectedFlow(testContext);
        } finally {
            FlowLabel.clear();
            RecordedFlowStore.clear();
            RecordedFlowStore.bind(null, null);
        }
    }

    @Override
    public void afterAll(TestContext testContext) {
        FlowSinks.unregister(CapturingFlowSink.INSTANCE);
        FlowRecordingSettings.reset();
    }

    private void assertExpectedFlow(TestContext testContext) {
        Optional<Method> testMethod = testContext.getMetadata(Method.class);
        if (testMethod.isEmpty()) {
            return;
        }
        ExpectedFlow expectedFlow = testMethod.get().getAnnotation(ExpectedFlow.class);
        if (expectedFlow == null) {
            return;
        }
        if (testContext.getMetadata(Throwable.class).isPresent()) {
            // the test method itself failed - its own failure is the one worth
            // reporting, and the recording of a half-run method says little
            return;
        }
        FlowDiff.Builder builder = FlowDiff.forRecordedFlows()
                .expected(resourceOf(expectedFlow, testContext.getTestClass(), testMethod.get()));
        if (expectedFlow.ignoring().length > 0) {
            builder.ignoring(expectedFlow.ignoring());
        }
        builder.assertEquals();
    }

    private String resourceOf(ExpectedFlow expectedFlow, Class<?> testClass, Method testMethod) {
        if (!expectedFlow.value().isEmpty()) {
            return expectedFlow.value();
        }
        String baseDirectory = FlowAssertConfig.text(
                FlowAssertConfig.EXPECTED_BASE_DIRECTORY_KEY, "flows");
        Optional<String> resolved = FlowDiff.resolveExpectedResource(
                baseDirectory, testClass, testMethod.getName());
        if (resolved.isPresent()) {
            return resolved.get();
        }
        List<String> candidates = FlowDiff.expectedResourceCandidates(
                baseDirectory, testClass, testMethod.getName());
        if (FlowAssertConfig.flag(FlowAssertConfig.CREATE_MISSING_EXPECTED_KEY, false)) {
            // the first candidate is the one the highest-priority dialect claims
            return candidates.get(0);
        }
        throw new IllegalStateException("@ExpectedFlow found no expected diagram for "
                + testClass.getSimpleName() + "#" + testMethod.getName() + ". Probed: "
                + String.join(", ", candidates)
                + ". Name one via @ExpectedFlow(\"...\"), or set "
                + FlowAssertConfig.CREATE_MISSING_EXPECTED_KEY
                + "=true to have this run create it from the recording.");
    }

    private boolean isRecording(TestContext testContext) {
        return testContext.getTestClass().getAnnotation(EnableFlowAssert.class) != null;
    }

    private Optional<String> methodNameOf(TestContext testContext) {
        return testContext.getMetadata(Method.class).map(Method::getName);
    }
}
