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
package org.os890.jawelte.module.datasource.impl;

import java.lang.reflect.Method;

import javax.sql.DataSource;

/**
 * Closing a {@link DataSource}, which the JDBC API does not define.
 *
 * <p>{@code javax.sql.DataSource} has no {@code close()}. A vendor
 * either implements {@link AutoCloseable}, exposes its own no-arg
 * {@code close()}, or holds nothing that needs closing at all — H2's
 * {@code JdbcDataSource} is in the third group, and that is not an
 * error.
 *
 * <p>Shared by the extension (which releases what it built when a
 * deployment-time build fails part-way) and the lifecycle adapter
 * (which releases everything when the test class is done).
 */
public abstract class DataSourceLifecycle {

    /** Suppress instantiation; the class is a static-method holder. */
    protected DataSourceLifecycle() {
    }

    /**
     * Close a data source that can be closed.
     *
     * @param dataSource the data source to release
     * @throws IllegalStateException if a close that exists fails
     */
    public static void closeIfCloseable(DataSource dataSource) {
        if (dataSource instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception closeFailure) {
                throw new IllegalStateException(
                        "Failed to close " + dataSource.getClass().getName(), closeFailure);
            }
            return;
        }
        Method close;
        try {
            close = dataSource.getClass().getMethod("close");
        } catch (NoSuchMethodException nothingToClose) {
            return;
        }
        try {
            close.invoke(dataSource);
        } catch (ReflectiveOperationException closeFailure) {
            throw new IllegalStateException(
                    "Failed to close " + dataSource.getClass().getName(), closeFailure);
        }
    }
}
