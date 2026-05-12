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
package org.os890.jawelte.module.contentdiff.api;

import java.util.List;
import java.util.Map;

/**
 * Immutable bag of options the {@link
 * org.os890.jawelte.module.contentdiff.api.port.DiffEngine} consumes.
 *
 * <p>Future option fields are added here without breaking the engine
 * SPI; engines that do not understand a new field treat it as a
 * no-op. {@code elValues} is one such field — the two built-in
 * engines interpolate {@code ${expr}} occurrences in the expected
 * document via Jakarta EL using this map, but a custom engine for a
 * different content type is free to ignore it.
 *
 * @param ignorePatterns  paths to skip; pattern dialect is engine-specific
 *                        (JSON-path for {@code application/json}, XPath
 *                        for {@code application/xml}); never {@code null}
 *                        (use an empty list for "no patterns")
 * @param unorderedArrays {@code true} switches array comparison to
 *                        multiset; default {@code false} means
 *                        index-wise comparison
 * @param elValues        key-value pairs the engine feeds to Jakarta EL
 *                        when interpolating the expected document;
 *                        never {@code null} (use an empty map for
 *                        "no values"); the value side accepts arbitrary
 *                        objects so EL can resolve property access and
 *                        method calls
 */
public record DiffOptions(
        List<String> ignorePatterns,
        boolean unorderedArrays,
        Map<String, Object> elValues) {

    /**
     * Defensively copies {@code ignorePatterns} and {@code elValues}
     * so a caller mutating the source collections after construction
     * cannot affect the record's view.
     *
     * @param ignorePatterns  paths to skip
     * @param unorderedArrays multiset array comparison toggle
     * @param elValues        Jakarta EL interpolation values
     */
    public DiffOptions {
        ignorePatterns = List.copyOf(ignorePatterns);
        elValues = Map.copyOf(elValues);
    }
}
