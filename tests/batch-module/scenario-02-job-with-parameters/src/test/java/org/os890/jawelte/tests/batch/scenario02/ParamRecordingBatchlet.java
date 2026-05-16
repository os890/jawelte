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
package org.os890.jawelte.tests.batch.scenario02;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.batch.api.Batchlet;
import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.context.JobContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("paramRecordingBatchlet")
@Dependent
public class ParamRecordingBatchlet implements Batchlet {

    static final AtomicReference<Properties> RECORDED = new AtomicReference<>();

    @Inject
    private JobContext jobContext;

    @Inject
    private JobOperator jobOperator;

    @Override
    public String process() {
        RECORDED.set(jobOperator.getParameters(jobContext.getExecutionId()));
        return "COMPLETED";
    }

    @Override
    public void stop() {
    }
}
