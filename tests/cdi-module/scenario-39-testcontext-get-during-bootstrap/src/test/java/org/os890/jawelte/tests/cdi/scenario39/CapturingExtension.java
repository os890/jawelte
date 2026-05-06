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
package org.os890.jawelte.tests.cdi.scenario39;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;

import org.os890.jawelte.core.api.port.TestContext;

public class CapturingExtension implements Extension {

    public static final AtomicReference<Class<?>> CAPTURED_TEST_CLASS = new AtomicReference<>();

    public CapturingExtension() {
    }

    void onBeforeBeanDiscovery(@Observes BeforeBeanDiscovery event) {
        TestContext active = TestContext.get();
        CAPTURED_TEST_CLASS.set(active.getTestClass());
    }
}
