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
package org.os890.jawelte.tests.batch.scenario13;

import java.util.concurrent.atomic.AtomicLong;

import jakarta.batch.api.Batchlet;
import jakarta.batch.runtime.context.JobContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

/**
 * Records its own executionId on start so the test can recover it
 * post-timeout, then sleeps long enough that the observer's tight
 * 500 ms timeout fires while the batchlet is still running.
 */
@Named("orphanBatchlet")
@Dependent
public class OrphanBatchlet implements Batchlet {

    public static final AtomicLong CAPTURED_EXECUTION_ID = new AtomicLong(-1);

    @Inject
    private JobContext jobContext;

    @Override
    public String process() throws InterruptedException {
        CAPTURED_EXECUTION_ID.set(jobContext.getExecutionId());
        Thread.sleep(2_500L);
        return "COMPLETED";
    }

    @Override
    public void stop() {
    }
}
