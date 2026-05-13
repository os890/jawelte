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

import java.util.Objects;

import org.os890.jawelte.module.dbtestdata.api.port.DbSeedEngine;

/**
 * Carrier for the options the seed-builder hands to the active
 * {@link DbSeedEngine}. The single field is the chosen seed
 * {@link SeedMode}; the record is immutable and {@code null}-rejecting
 * by canonical-constructor validation.
 *
 * @param mode the SQL-shape the engine performs against the dataset
 */
public record SeedSpec(SeedMode mode) {

    /** Canonical constructor; null {@code mode} is rejected up-front. */
    public SeedSpec {
        Objects.requireNonNull(mode, "mode");
    }

    /**
     * Strategy the {@link DbSeedEngine} runs against a dataset.
     */
    public enum SeedMode {

        /**
         * DELETE every row in the dataset's tables (in reverse
         * foreign-key dependency order), then INSERT the dataset.
         * The default mode of {@code DbSeed.Builder.cleanInsert()}.
         */
        CLEAN_INSERT,

        /**
         * INSERT only; duplicate PK propagates as
         * {@link RuntimeException} wrapping the underlying
         * {@link java.sql.SQLException}.
         */
        INSERT,

        /**
         * UPDATE existing rows by primary key; missing rows propagate
         * as {@link RuntimeException}.
         */
        UPDATE,

        /**
         * Upsert by primary key — INSERT when the row is absent,
         * UPDATE when present. Safe under circular foreign-key
         * dependencies because no DELETE step fires.
         */
        REFRESH
    }
}
