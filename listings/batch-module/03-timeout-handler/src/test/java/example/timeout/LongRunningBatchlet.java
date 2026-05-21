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
package example.timeout;

import jakarta.batch.api.Batchlet;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Named;

@Named("longRunningBatchlet")
@Dependent
public class LongRunningBatchlet implements Batchlet {

    @Override
    public String process() throws InterruptedException {
        // Sleep longer than the observer's timeout so the alternative
        // TimeoutHandler fires while the job is still non-terminal.
        Thread.sleep(3_000L);
        return "COMPLETED";
    }

    @Override
    public void stop() {
    }
}
