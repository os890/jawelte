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
package org.os890.jawelte.tests.contentdiff.scenario25;

import java.util.List;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.contentdiff.api.DiffOptions;
import org.os890.jawelte.module.contentdiff.api.Difference;
import org.os890.jawelte.module.contentdiff.api.port.DiffEngine;

/**
 * Test-only JSON engine registered at {@code @Priority(Integer.MAX_VALUE - 1)}
 * so it wins over the built-in {@code JsonDiffEngine}
 * ({@code @Priority(Integer.MAX_VALUE)}). The implementation
 * returns an empty difference list regardless of input — when this
 * engine is the active one, {@code ContentDiff.forJson(...).assertEquals()}
 * reports no diff even for inputs the built-in engine would flag.
 */
@Priority(Integer.MAX_VALUE - 1)
public class TestScenarioWinningJsonEngine implements DiffEngine {

    public TestScenarioWinningJsonEngine() {
    }

    @Override
    public String contentType() {
        return "application/json";
    }

    @Override
    public List<Difference> diff(String expected, String actual, DiffOptions options) {
        return List.of();
    }
}
