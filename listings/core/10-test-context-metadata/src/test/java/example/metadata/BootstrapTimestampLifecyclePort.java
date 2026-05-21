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
package example.metadata;

import java.util.concurrent.atomic.AtomicReference;

import org.os890.jawelte.core.api.port.TestContext;
import org.os890.jawelte.core.api.port.TestModuleLifecyclePort;

/**
 * Custom {@code TestModuleLifecyclePort} demonstrating the
 * metadata API: bind a per-class value in {@code beforeAll}, read
 * it back in {@code beforeEach}, and leak both values to a static
 * carrier so the test class can verify what travelled across the
 * lifecycle callbacks.
 */
public class BootstrapTimestampLifecyclePort implements TestModuleLifecyclePort {

    /** Set in {@code beforeAll}; the same record retrieved in {@code beforeEach} must equal this. */
    public static final AtomicReference<StartupRecord> BOUND_IN_BEFORE_ALL = new AtomicReference<>();

    /** Set in {@code beforeEach} from {@code testContext.getMetadata(StartupRecord.class)}. */
    public static final AtomicReference<StartupRecord> SEEN_IN_BEFORE_EACH = new AtomicReference<>();

    @Override
    public void beforeAll(TestContext testContext) {
        StartupRecord record = new StartupRecord(System.currentTimeMillis());
        testContext.bindMetadata(StartupRecord.class, record);
        BOUND_IN_BEFORE_ALL.set(record);
    }

    @Override
    public void beforeEach(TestContext testContext) {
        SEEN_IN_BEFORE_EACH.set(testContext.getMetadata(StartupRecord.class).orElse(null));
    }
}
