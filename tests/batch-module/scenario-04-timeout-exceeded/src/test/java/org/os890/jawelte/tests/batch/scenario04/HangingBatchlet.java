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
package org.os890.jawelte.tests.batch.scenario04;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.batch.api.Batchlet;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;

/**
 * Sleeps long enough (3 s) that the observer's 500 ms timeout
 * fires before the batchlet returns. Cooperatively interruptible
 * so the orphaned jBatch thread doesn't outlive the test JVM.
 */
@Named("hangingBatchlet")
@Dependent
public class HangingBatchlet implements Batchlet {

    private final AtomicBoolean stopped = new AtomicBoolean();

    @Override
    public String process() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000L;
        while (!stopped.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L);
        }
        return "COMPLETED";
    }

    @Override
    public void stop() {
        stopped.set(true);
    }
}
