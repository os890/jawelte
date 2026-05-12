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
package org.os890.jawelte.tests.contentdiff.scenario24;

import java.util.List;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.contentdiff.api.DiffOptions;
import org.os890.jawelte.module.contentdiff.api.Difference;
import org.os890.jawelte.module.contentdiff.api.port.DiffEngine;

/**
 * Test-only CSV engine registered to verify that a custom
 * {@link DiffEngine} for a new content type is picked up by
 * {@link java.util.ServiceLoader} alongside the two built-ins.
 * Not reachable through {@code ContentDiff.forJson(...)} /
 * {@code forXml(...)} — the api only exposes those two factories —
 * so the scenario reaches the engine directly via
 * {@code ServiceLoader.load(DiffEngine.class)}.
 */
@Priority(Integer.MAX_VALUE)
public class TestScenarioCsvEngine implements DiffEngine {

    public TestScenarioCsvEngine() {
    }

    @Override
    public String contentType() {
        return "text/csv";
    }

    @Override
    public List<Difference> diff(String expected, String actual, DiffOptions options) {
        return List.of();
    }
}
