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
package org.os890.jawelte.tests.batch.scenario09;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.batch.api.Batchlet;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@Named("injectingBatchlet")
@Dependent
public class InjectingBatchlet implements Batchlet {

    static final AtomicReference<String> GREETING = new AtomicReference<>();

    @Inject
    private GreeterBean greeter;

    @Override
    public String process() {
        GREETING.set(greeter.greet("batch"));
        return "COMPLETED";
    }

    @Override
    public void stop() {
    }
}
