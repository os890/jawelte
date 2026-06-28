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
package org.os890.jawelte.tests.batch.scenario16;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.batch.api.BatchExecution;
import org.os890.jawelte.module.batch.api.port.TimeoutHandler;

/**
 * Test-only {@link TimeoutHandler} whose construction count reveals how
 * many times the observer resolved the SPI. {@code TestContext.loadService}
 * instantiates each provider once per call, so the construction count
 * equals the number of resolutions: once per JVM on the static-field
 * implementation, once per container after the fix.
 *
 * <p>{@code @Priority(50)} so it wins the SPI sort over the default
 * {@code ThrowingTimeoutHandler}. {@code onTimeout} is a no-op (the test
 * job completes, so it is never invoked).
 */
@Priority(50)
public class CountingTimeoutHandler implements TimeoutHandler {

    static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

    public CountingTimeoutHandler() {
        CONSTRUCTIONS.incrementAndGet();
    }

    @Override
    public void onTimeout(BatchExecution event, long executionId, jakarta.batch.runtime.JobExecution lastSnapshot) {
        // No-op: the test job completes, so this is never called.
    }
}
