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
 * Single-use fluent builder for XML content diffs. Returned by
 * {@link ContentDiff#forXml(String)}; not reusable across two
 * assertions and not thread-safe.
 *
 * <p>Accepts XPath-style ignore patterns:
 * <ul>
 *   <li>{@code /root/field} — absolute XPath;</li>
 *   <li>{@code //field} — recursive: skip the element at any depth.</li>
 * </ul>
 */
public class XmlBuilder extends AbstractContentBuilder<XmlBuilder> {

    XmlBuilder(DiffEngine engine, String actualContent, List<String> ignoreDefaults) {
        super(engine, actualContent, ignoreDefaults);
    }

    @Override
    String formatName() {
        return ContentDiff.XML_FORMAT_NAME;
    }
}
