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
package example.jobparams;

import java.util.Properties;

import jakarta.batch.api.Batchlet;
import jakarta.batch.operations.JobOperator;
import jakarta.batch.runtime.context.JobContext;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("echoBatchlet")
@Dependent
public class EchoBatchlet implements Batchlet {

    @Inject
    private JobContext jobContext;

    @Inject
    private JobOperator jobOperator;

    @Override
    public String process() {
        // BatchExecution.param() values come back through
        // jobOperator.getParameters(executionId) — NOT
        // jobContext.getProperties() (which holds JSL <properties>).
        Properties parameters = jobOperator.getParameters(jobContext.getExecutionId());
        jobContext.setExitStatus(parameters.getProperty("greeting") + "=" + parameters.getProperty("target"));
        return "COMPLETED";
    }

    @Override
    public void stop() {
    }
}
