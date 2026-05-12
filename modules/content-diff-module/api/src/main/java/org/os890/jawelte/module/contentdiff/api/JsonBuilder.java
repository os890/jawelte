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

import org.os890.jawelte.module.contentdiff.api.port.DiffEngine;

/**
 * Single-use fluent builder for JSON content diffs. Returned by
 * {@link ContentDiff#forJson(String)}; not reusable across two
 * assertions and not thread-safe.
 *
 * <p>Accepts JSON-path-style ignore patterns:
 * <ul>
 *   <li>{@code $.field} — skip a top-level field;</li>
 *   <li>{@code $.items[*].field} — skip a field in every element
 *       of an array;</li>
 *   <li>{@code $..field} — recursive descent: skip the field at any
 *       depth.</li>
 * </ul>
 */
public class JsonBuilder extends AbstractContentBuilder<JsonBuilder> {

    JsonBuilder(DiffEngine engine, String actualContent, List<String> ignoreDefaults) {
        super(engine, actualContent, ignoreDefaults);
    }

    @Override
    String formatName() {
        return ContentDiff.JSON_FORMAT_NAME;
    }
}
