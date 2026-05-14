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
package org.os890.jawelte.module.testcontrol.impl.adapter.data;

import org.os890.jawelte.core.api.port.TestContext;

/**
 * Internal marker bound on {@link TestContext} by
 * {@link TestDataHandler#onAfterTestTransaction} once the
 * transactional verification path has run for the current test
 * method. The lifecycle adapter's {@code afterEach} checks for
 * {@code TestContext.getMetadata(VerificationCompleted.class)} and
 * skips its non-transactional {@code dbExpected/} fallback when the
 * marker is present, so a transactional test does not have its
 * verification run twice.
 *
 * <p>Lifetime is one test method: the lifecycle adapter unbinds the
 * key at the end of its {@code afterEach}.
 *
 * <p>Not part of the public API — package-private to the
 * {@code data} adapter package; only the {@link TestDataHandler}
 * and {@code TestControlLifecycleAdapter} reference it.
 */
public class VerificationCompleted {

    /** Singleton instance bound under the {@link VerificationCompleted} key. */
    public static final VerificationCompleted INSTANCE = new VerificationCompleted();

    private VerificationCompleted() {
    }
}
