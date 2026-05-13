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

import org.os890.jawelte.module.dbtestdata.api.DbSeed;

/**
 * Pluggable dataset-loader. The {@code DbSeed} fluent api resolves
 * the active impl by matching {@link #format()} against the
 * {@code format} chosen on the builder; multiple impls can co-exist
 * (one per format), and within a format the project-wide
 * {@code ServicePriorityResolver} picks the lowest-{@code @Priority}
 * winner.
 *
 * <p>Implementations are JVM-lifetime singletons (loaded once via
 * {@link java.util.ServiceLoader}, cached for the JVM); they must be
 * thread-safe and hold no per-call state.
 *
 * <p>The default impl shipped by db-testdata-module/impl is
 * {@code DbUnitXmlSeedEngine} with {@code format() == "dbunit-xml"}
 * at {@code @Priority(Integer.MAX_VALUE)}.
 */
public interface DbSeedEngine {

    /**
     * Stable identifier of the dataset format this engine handles.
     * The {@code DbSeed} builder matches the value the test author
     * passed to {@code format(...)} against this string. Never
     * throws; implementations return a non-{@code null} value that
     * does not change across calls.
     *
     * @return the format identifier (e.g. {@code "dbunit-xml"})
     */
    String format();

    /**
     * Write the dataset {@code datasetContent} to {@code connection}
     * according to the chosen {@link DbSeed.SeedSpec#mode()}. The connection
     * is supplied by the caller (either directly via
     * {@code DbSeed.forConnection(...)} or unwrapped from the active
     * persistence unit via
     * {@code DbSeed.forPersistenceUnit(...)}); the engine must never
     * close, commit, or roll back this connection.
     *
     * @param connection     open, caller-owned JDBC connection
     * @param datasetContent post-EL-interpolation dataset text
     * @param options        seed options (currently only the mode)
     * @throws RuntimeException wrapping any {@link java.sql.SQLException}
     *                          or parse error; the api re-wraps with
     *                          a {@code "[DbSeed] Failed to seed
     *                          dataset: " + cause} prefix before it
     *                          reaches the caller
     */
    void seed(Connection connection, String datasetContent, DbSeed.SeedSpec options);
}
