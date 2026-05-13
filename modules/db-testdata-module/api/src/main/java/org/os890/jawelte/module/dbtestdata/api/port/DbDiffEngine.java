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
package org.os890.jawelte.module.dbtestdata.api.port;

import java.sql.Connection;
import java.util.List;

import org.os890.jawelte.module.dbtestdata.api.DbDiff;
import org.os890.jawelte.module.dbtestdata.api.DbDiff.Difference;

/**
 * Pluggable dataset-verifier. Mirror of {@link DbSeedEngine} for the
 * diff side: {@code DbDiff} matches {@link #format()} against the
 * builder's {@code format(...)} value, and the active impl returns a
 * list of typed {@link Difference}s — the api owns the
 * {@link AssertionError} formatting; engines never produce strings.
 *
 * <p>Implementations are JVM-lifetime singletons, loaded via
 * {@link java.util.ServiceLoader}, cached for the JVM, and must be
 * thread-safe.
 *
 * <p>The default impl is {@code DbUnitXmlDiffEngine} with
 * {@code format() == "dbunit-xml"} at
 * {@code @Priority(Integer.MAX_VALUE)}.
 */
public interface DbDiffEngine {

    /**
     * Stable identifier of the dataset format this engine compares
     * against. Never throws.
     *
     * @return the format identifier (e.g. {@code "dbunit-xml"})
     */
    String format();

    /**
     * Compare the database state on {@code connection} against
     * {@code expectedContent}, returning every cell-, row-, or
     * column-level discrepancy as a {@link Difference}.
     *
     * <p>The returned list is immutable and possibly empty (empty
     * == no differences). The engine does not commit, close, or
     * roll back the connection; that is the caller's responsibility.
     *
     * @param connection      open, caller-owned JDBC connection
     * @param expectedContent post-EL-interpolation expected dataset
     * @param options         diff options (ignore patterns, subset
     *                        mode, unordered tables, boolean
     *                        extensions)
     * @return the differences (immutable; possibly empty)
     * @throws RuntimeException wrapping any {@link java.sql.SQLException}
     *                          or parse error; the api re-wraps with
     *                          a {@code "[DbDiff] Failed to read
     *                          database: " + cause} prefix before it
     *                          reaches the caller
     */
    List<Difference> diff(Connection connection, String expectedContent, DbDiff.DiffSpec options);
}
