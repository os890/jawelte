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
package org.os890.jawelte.module.dbtestdata.api;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.os890.jawelte.module.dbtestdata.api.SeedSpec.SeedMode;
import org.os890.jawelte.module.dbtestdata.api.port.DbSeedEngine;
import org.os890.jawelte.module.dbtestdata.api.port.ELInterpolator;

/**
 * Single-use fluent configuration returned by {@link DbSeed}'s static
 * factories. Accumulates the dataset source, the SQL-shape mode,
 * the format identifier, and the EL value bindings, then executes
 * the seed via {@link #execute()}.
 *
 * <p>Builders are not thread-safe and must not be reused after
 * {@link #execute()} returns or throws.
 */
public class DbSeedBuilder {

    private final Supplier<Connection> connectionSupplier;

    private String classpathResource;

    private String inlineContent;

    private String format = DatasetSupport.DEFAULT_FORMAT;

    private SeedMode mode = SeedMode.CLEAN_INSERT;

    private final Map<String, Object> values = new LinkedHashMap<>();

    /** Constructor visible to {@link DbSeed} only. */
    DbSeedBuilder(Supplier<Connection> connectionSupplier) {
        this.connectionSupplier = connectionSupplier;
    }

    /**
     * Load the dataset content from {@code classpathResource} via
     * the thread context classloader. Mutually exclusive with
     * {@link #datasetContent(String)}.
     *
     * @param classpathResource the classpath-relative path
     * @return this builder for chaining
     * @throws IllegalStateException if {@link #datasetContent(String)}
     *         has already been called
     */
    public DbSeedBuilder dataset(String classpathResource) {
        if (inlineContent != null) {
            throw new IllegalStateException(
                    "dataset(...) and datasetContent(...) are mutually exclusive");
        }
        this.classpathResource = Objects.requireNonNull(classpathResource, "classpathResource");
        return this;
    }

    /**
     * Use {@code content} as the dataset directly. Mutually
     * exclusive with {@link #dataset(String)}.
     *
     * @param content the dataset text
     * @return this builder for chaining
     * @throws IllegalStateException if {@link #dataset(String)} has
     *         already been called
     */
    public DbSeedBuilder datasetContent(String content) {
        if (classpathResource != null) {
            throw new IllegalStateException(
                    "dataset(...) and datasetContent(...) are mutually exclusive");
        }
        this.inlineContent = Objects.requireNonNull(content, "content");
        return this;
    }

    /**
     * Pick the dataset format identifier. Defaults to
     * {@code "dbunit-xml"} when this method is not called. Unknown
     * formats raise {@link IllegalArgumentException} from
     * {@link #execute()}, not from here.
     *
     * @param format the format identifier
     * @return this builder for chaining
     */
    public DbSeedBuilder format(String format) {
        this.format = Objects.requireNonNull(format, "format");
        return this;
    }

    /**
     * Select {@link SeedMode#CLEAN_INSERT} — the default mode. DELETE
     * every row in the dataset's tables in reverse foreign-key order,
     * then INSERT.
     *
     * @return this builder for chaining
     */
    public DbSeedBuilder cleanInsert() {
        this.mode = SeedMode.CLEAN_INSERT;
        return this;
    }

    /**
     * Select {@link SeedMode#INSERT}. INSERT only; duplicate PK
     * propagates as {@link RuntimeException}.
     *
     * @return this builder for chaining
     */
    public DbSeedBuilder insert() {
        this.mode = SeedMode.INSERT;
        return this;
    }

    /**
     * Select {@link SeedMode#UPDATE}. UPDATE existing rows by PK;
     * missing rows propagate as {@link RuntimeException}.
     *
     * @return this builder for chaining
     */
    public DbSeedBuilder update() {
        this.mode = SeedMode.UPDATE;
        return this;
    }

    /**
     * Select {@link SeedMode#REFRESH}. Upsert (INSERT when absent,
     * UPDATE when present). Safe under circular foreign-key
     * dependencies.
     *
     * @return this builder for chaining
     */
    public DbSeedBuilder refresh() {
        this.mode = SeedMode.REFRESH;
        return this;
    }

    /**
     * Add EL value bindings. Cumulative — multiple calls union the
     * bindings (later keys override earlier ones).
     *
     * @param values map of EL bindings
     * @return this builder for chaining
     */
    public DbSeedBuilder withValues(Map<String, Object> values) {
        this.values.putAll(Objects.requireNonNull(values, "values"));
        return this;
    }

    /**
     * Resolve the connection, load and interpolate the dataset,
     * resolve the active {@link DbSeedEngine} for {@link #format(String)},
     * and call {@code engine.seed(connection, content, SeedSpec)}.
     * Wraps any engine-side failure in {@code RuntimeException}
     * prefixed with {@code "[DbSeed] Failed to seed dataset: "}.
     *
     * @throws IllegalStateException    if neither {@link #dataset(String)}
     *                                  nor {@link #datasetContent(String)}
     *                                  was called
     * @throws IllegalArgumentException for an unknown format or a
     *                                  missing classpath resource
     */
    public void execute() {
        String content = loadContent();
        ELInterpolator interpolator = DatasetSupport.resolveInterpolator();
        InterpolationContext context = new InterpolationContext(values, Map.of(), List.of());
        String interpolated = interpolator.interpolateAll(content, context);
        DbSeedEngine engine = DatasetSupport.resolveSeedEngine(format);
        Connection connection = connectionSupplier.get();
        try {
            engine.seed(connection, interpolated, new SeedSpec(mode));
        } catch (RuntimeException seedFailure) {
            throw new RuntimeException(
                    "[DbSeed] Failed to seed dataset: " + seedFailure.getMessage(), seedFailure);
        }
    }

    private String loadContent() {
        if (inlineContent != null) {
            return inlineContent;
        }
        if (classpathResource == null) {
            throw new IllegalStateException(
                    "Neither dataset(...) nor datasetContent(...) was called");
        }
        return DatasetSupport.loadClasspathResource(classpathResource);
    }
}
