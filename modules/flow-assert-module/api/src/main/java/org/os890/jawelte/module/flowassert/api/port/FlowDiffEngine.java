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
package org.os890.jawelte.module.flowassert.api.port;

import java.util.List;

import org.os890.jawelte.module.flowassert.api.FlowDiff;
import org.os890.jawelte.module.flowassert.api.FlowStep;

/**
 * The comparison itself: two parsed diagrams in, the differences out.
 * Notation-agnostic — it works on the canonical {@link FlowStep}
 * model, which is why a custom {@link FlowDialect} inherits the
 * built-in comparison instead of having to bring one.
 *
 * <p>Exactly one engine is active per JVM. Implementations register
 * via
 * {@code META-INF/services/org.os890.jawelte.module.flowassert.api.port.FlowDiffEngine}
 * and are resolved through
 * {@code TestContext.loadService(FlowDiffEngine.class)}, so a
 * consumer swaps the built-in one out by shipping an implementation
 * with a lower numeric {@code jakarta.annotation.Priority}.
 *
 * <p>Implementations must be thread-safe and must not modify the
 * lists they are handed.
 */
public interface FlowDiffEngine {

    /**
     * Compare the expected steps against the recorded ones. An empty
     * result means the recording matches.
     *
     * <p>Which steps matter is the engine's decision, guided by
     * {@code spec}: notation boilerplate and volatile annotations are
     * expected to be ignored unless the spec asks otherwise, and the
     * ignore patterns of the spec are expected to be applied to both
     * sides before comparing.
     *
     * @param expected the steps parsed from the expected diagram;
     *                 never {@code null}
     * @param actual   the steps parsed from the rendered recording;
     *                 never {@code null}
     * @param spec     what to compare and what to leave out; never
     *                 {@code null}
     * @return the differences, most relevant first; never
     *         {@code null}, possibly empty
     */
    List<FlowDiff.Difference> diff(List<FlowStep> expected, List<FlowStep> actual, FlowDiff.DiffSpec spec);
}
