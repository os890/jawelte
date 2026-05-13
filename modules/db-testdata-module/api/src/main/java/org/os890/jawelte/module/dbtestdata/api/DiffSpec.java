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

import java.util.List;
import java.util.Objects;

import org.os890.jawelte.module.dbtestdata.api.port.DbDiffEngine;

/**
 * Carrier for the options the diff-builder hands to the active
 * {@link DbDiffEngine}. The six fields collapse the builder's
 * {@code ignoring(...)} / {@code subsetOnly()} /
 * {@code unorderedTables(...)} / boolean-extension lists plus the
 * EL {@link InterpolationContext} into a single immutable struct.
 *
 * @param ignorePatterns       column patterns to skip; values follow
 *                             the {@code *.COLUMN} (any table) or
 *                             {@code TABLE.COLUMN} (specific) syntax
 * @param subsetOnly           {@code true} restricts the comparison to
 *                             the tables / columns present in the
 *                             expected dataset; {@code false} reports
 *                             extra rows and extra columns as well
 * @param unorderedTables      uppercase table names whose rows are
 *                             compared as a multiset (row-order
 *                             insensitive)
 * @param booleanTrueValues    additional values, beyond the built-in
 *                             list ({@code true}, {@code 1}, {@code yes},
 *                             {@code y}, {@code on}), that normalise to
 *                             the boolean {@code true} during cell
 *                             comparison
 * @param booleanFalseValues   additional values, beyond the built-in
 *                             list ({@code false}, {@code 0}, {@code no},
 *                             {@code n}, {@code off}), that normalise to
 *                             the boolean {@code false}
 * @param interpolationContext bindings the diff engine forwards to the
 *                             active EL interpolator when it evaluates
 *                             {@code #{&hellip;}} per-cell predicate
 *                             markers; the builder constructs this from
 *                             its {@code withValues(...)} /
 *                             {@code withBean(...)} /
 *                             {@code withFunction(...)} registrations
 */
public record DiffSpec(
        List<String> ignorePatterns,
        boolean subsetOnly,
        List<String> unorderedTables,
        List<String> booleanTrueValues,
        List<String> booleanFalseValues,
        InterpolationContext interpolationContext) {

    /**
     * Canonical constructor. Defensively copies every list so the
     * record remains immutable even if the caller mutates the
     * source after construction. {@code interpolationContext} is
     * already immutable (record).
     */
    public DiffSpec {
        ignorePatterns = List.copyOf(Objects.requireNonNull(ignorePatterns, "ignorePatterns"));
        unorderedTables = List.copyOf(Objects.requireNonNull(unorderedTables, "unorderedTables"));
        booleanTrueValues = List.copyOf(Objects.requireNonNull(booleanTrueValues, "booleanTrueValues"));
        booleanFalseValues = List.copyOf(Objects.requireNonNull(booleanFalseValues, "booleanFalseValues"));
        Objects.requireNonNull(interpolationContext, "interpolationContext");
    }
}
