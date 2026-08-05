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
package org.os890.jawelte.module.flowassert.impl.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Longest-common-subsequence alignment of two key sequences — the
 * step of the comparison that decides <em>where</em> two diagrams
 * start to differ instead of just <em>that</em> they do.
 *
 * <p>Aligning rather than comparing line by line is what keeps one
 * inserted call from reporting every following line as changed: the
 * common subsequence stays matched and the insertion shows up as the
 * single operation it is.
 *
 * <p>{@code abstract} plus a private constructor per the project's
 * static-utility class convention.
 */
public abstract class StepAlignment {

    private StepAlignment() {
    }

    /**
     * Align two key sequences.
     *
     * @param expected the keys of the expected side; must not be {@code null}
     * @param actual   the keys of the recorded side; must not be {@code null}
     * @return the operations in document order; never {@code null}
     */
    public static List<Operation> align(List<String> expected, List<String> actual) {
        int expectedSize = expected.size();
        int actualSize = actual.size();
        int[][] common = new int[expectedSize + 1][actualSize + 1];
        for (int i = expectedSize - 1; i >= 0; i--) {
            for (int j = actualSize - 1; j >= 0; j--) {
                common[i][j] = expected.get(i).equals(actual.get(j))
                        ? common[i + 1][j + 1] + 1
                        : Math.max(common[i + 1][j], common[i][j + 1]);
            }
        }

        List<Operation> operations = new ArrayList<>(expectedSize + actualSize);
        int i = 0;
        int j = 0;
        while (i < expectedSize && j < actualSize) {
            if (expected.get(i).equals(actual.get(j))) {
                operations.add(new Operation(i++, j++));
            } else if (common[i + 1][j] >= common[i][j + 1]) {
                operations.add(new Operation(i++, -1));
            } else {
                operations.add(new Operation(-1, j++));
            }
        }
        while (i < expectedSize) {
            operations.add(new Operation(i++, -1));
        }
        while (j < actualSize) {
            operations.add(new Operation(-1, j++));
        }
        return List.copyOf(operations);
    }

    /**
     * One alignment step: a match carries both indices, a deletion
     * only the expected one, an insertion only the actual one.
     *
     * @param expectedIndex index into the expected sequence, or
     *                      {@code -1} for an insertion
     * @param actualIndex   index into the recorded sequence, or
     *                      {@code -1} for a deletion
     */
    public record Operation(int expectedIndex, int actualIndex) {
    }
}
