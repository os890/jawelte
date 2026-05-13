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
package org.os890.jawelte.module.dbtestdata.impl.util;

/**
 * Callback the diff engine hands to {@link MarkerComparator} so the
 * comparator can dispatch {@code #{&hellip;}} markers without pulling
 * the api-side EL interpolator and {@code InterpolationContext} into
 * its own surface. The diff engine constructs the lambda from the
 * active {@code ELInterpolator} plus the per-call interpolation
 * bindings; the comparator only ever sees the abstract predicate.
 */
@FunctionalInterface
public interface CellPredicateEvaluator {

    /**
     * Evaluate {@code expression} as a boolean predicate against
     * {@code actualValue}.
     *
     * @param expression  the full {@code #{&hellip;}} expression as
     *                    written in the expected cell
     * @param actualValue the value the database returned for the cell
     * @return the predicate's boolean result
     * @throws RuntimeException on evaluation failure or non-boolean
     *                          result (strict-EL contract)
     */
    boolean evaluate(String expression, Object actualValue);
}
