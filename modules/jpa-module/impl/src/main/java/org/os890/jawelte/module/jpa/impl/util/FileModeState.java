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
package org.os890.jawelte.module.jpa.impl.util;

/**
 * Per-test-class tracker for {@code @PersistenceConfig(fileMode=true)}
 * runs. Bound on
 * {@code org.os890.jawelte.core.api.port.TestContext} via
 * {@link org.os890.jawelte.core.api.port.TestContext#bindMetadata(Class, Object)}
 * by {@code JpaLifecycleAdapter.beforeAll} when file mode is
 * active; absent for in-memory mode.
 *
 * <p>The lifecycle adapter consults this tracker in
 * {@code beforeEach} to decide whether the next {@code @Test}
 * method should run (first method) or be aborted via
 * {@code TestAbortedException} (every subsequent method),
 * preserving the H2 file state from the first method for manual
 * inspection.
 *
 * <p>Holds the resolved file-system path so the abort message can
 * point the developer at the file directly.
 */
public class FileModeState {

    private final String filePath;

    private boolean firstMethodExecuted;

    /**
     * Construct a tracker for a file-mode test class.
     *
     * @param filePath the resolved directory path for the H2 file
     *                 (already including any
     *                 {@code @PersistenceConfig.filePath} or MP
     *                 Config override)
     */
    public FileModeState(String filePath) {
        this.filePath = filePath;
    }

    /**
     * Get the resolved file-system path of the H2 file directory.
     *
     * @return the directory path
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * Whether at least one {@code @Test} method has already
     * completed under file mode for this test class.
     *
     * @return {@code true} once the first method has executed,
     *         {@code false} before
     */
    public boolean isFirstMethodExecuted() {
        return firstMethodExecuted;
    }

    /**
     * Mark that a {@code @Test} method has just completed. Idempotent
     * — subsequent calls are no-ops.
     */
    public void markFirstMethodExecuted() {
        firstMethodExecuted = true;
    }
}
