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
package org.os890.jawelte.module.flowassert.impl.adapter.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.annotation.Priority;

import org.os890.jawelte.module.flowassert.api.FlowDiff;
import org.os890.jawelte.module.flowassert.api.FlowStep;
import org.os890.jawelte.module.flowassert.api.port.FlowDiffEngine;
import org.os890.jawelte.module.flowassert.impl.util.CallPatterns;
import org.os890.jawelte.module.flowassert.impl.util.DiagramChain;
import org.os890.jawelte.module.flowassert.impl.util.StepAlignment;

/**
 * The built-in comparison: chains are matched first, then the steps
 * inside each matched pair.
 *
 * <p>Matching the chains first is what keeps a diff readable. An
 * outermost call that happened once too often would otherwise shift
 * every following line and turn one mistake into a diff as long as
 * the diagram; here it is reported as a single
 * {@code UNEXPECTED_CHAIN} and the remaining blocks are still compared
 * pairwise.
 *
 * <p>Inside a chain the two step sequences are aligned by longest
 * common subsequence. A gap in that alignment holding one step on
 * either side is reported as the one thing that changed —
 * {@code DIFFERENT_TARGET}, {@code DIFFERENT_SIGNATURE},
 * {@code DIFFERENT_RETURN}, {@code LOOP_COUNT} — rather than as a
 * deletion plus an insertion. Steps that exist on both sides at
 * different positions are reported as {@code WRONG_ORDER}.
 *
 * <p>Participant declarations are <strong>not</strong> compared.
 * A lane exists because a call goes to it, so comparing the lanes
 * reports a second time what the call comparison already reported -
 * and it would fight the ignore lists, where a deliberately ignored
 * call leaves its lane declared on one side only. Nothing a recording
 * can produce is missed by leaving them out: a lane a recording never
 * calls into cannot exist.
 *
 * <p>Registered at {@code @Priority(Integer.MAX_VALUE)}: an
 * implementation with a lower numeric priority replaces it wholesale.
 */
@Priority(Integer.MAX_VALUE)
public class AlignmentFlowDiffEngine implements FlowDiffEngine {

    /** No-arg constructor required by SPI {@code ServiceLoader} lookup. */
    public AlignmentFlowDiffEngine() {
    }

    @Override
    public List<FlowDiff.Difference> diff(
            List<FlowStep> expected, List<FlowStep> actual, FlowDiff.DiffSpec spec) {
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(spec, "spec");

        List<FlowStep> expectedSteps = significant(expected, spec);
        List<FlowStep> actualSteps = significant(actual, spec);

        List<FlowDiff.Difference> differences = new ArrayList<>();
        differences.addAll(compareChains(
                DiagramChain.of(expectedSteps), DiagramChain.of(actualSteps), spec));
        differences.addAll(compareTitles(expectedSteps, actualSteps, spec));
        return List.copyOf(differences);
    }

    private List<FlowDiff.Difference> compareChains(
            List<DiagramChain> expected, List<DiagramChain> actual, FlowDiff.DiffSpec spec) {
        List<FlowDiff.Difference> differences = new ArrayList<>();
        List<String> expectedLabels = labelsOf(expected);
        List<String> actualLabels = labelsOf(actual);

        for (StepAlignment.Operation operation : StepAlignment.align(expectedLabels, actualLabels)) {
            DiagramChain expectedChain = operation.expectedIndex() < 0
                    ? null : expected.get(operation.expectedIndex());
            DiagramChain actualChain = operation.actualIndex() < 0
                    ? null : actual.get(operation.actualIndex());
            if (expectedChain != null && actualChain != null) {
                differences.addAll(compareSteps(expectedChain, actualChain, spec));
            } else if (expectedChain != null) {
                differences.add(chainDifference(FlowDiff.Difference.Kind.MISSING_CHAIN, expectedChain, null));
            } else {
                differences.add(chainDifference(FlowDiff.Difference.Kind.UNEXPECTED_CHAIN, null, actualChain));
            }
        }
        return differences;
    }

    private FlowDiff.Difference chainDifference(
            FlowDiff.Difference.Kind kind, DiagramChain expected, DiagramChain actual) {
        DiagramChain present = expected == null ? actual : expected;
        return new FlowDiff.Difference(kind,
                expected == null ? FlowDiff.Difference.MISSING : "chain " + expected.label(),
                actual == null ? FlowDiff.Difference.MISSING : "chain " + actual.label(),
                expected == null ? 0 : expected.lineNumber(),
                actual == null ? 0 : actual.lineNumber(),
                present.chainIndex(), 0);
    }

    private List<FlowDiff.Difference> compareSteps(
            DiagramChain expected, DiagramChain actual, FlowDiff.DiffSpec spec) {
        List<FlowStep> expectedSteps = expected.steps();
        List<FlowStep> actualSteps = actual.steps();
        List<FlowDiff.Difference> differences = new ArrayList<>();

        List<FlowStep> pendingExpected = new ArrayList<>();
        List<FlowStep> pendingActual = new ArrayList<>();
        for (StepAlignment.Operation operation
                : StepAlignment.align(keysOf(expectedSteps, spec), keysOf(actualSteps, spec))) {
            if (operation.expectedIndex() >= 0 && operation.actualIndex() >= 0) {
                differences.addAll(drainGap(pendingExpected, pendingActual, expected.chainIndex(), spec));
                continue;
            }
            if (operation.expectedIndex() >= 0) {
                pendingExpected.add(expectedSteps.get(operation.expectedIndex()));
            } else {
                pendingActual.add(actualSteps.get(operation.actualIndex()));
            }
        }
        differences.addAll(drainGap(pendingExpected, pendingActual, expected.chainIndex(), spec));
        return mergeReorderings(differences);
    }

    private List<FlowDiff.Difference> drainGap(
            List<FlowStep> expected, List<FlowStep> actual, int chainIndex, FlowDiff.DiffSpec spec) {
        List<FlowDiff.Difference> differences = new ArrayList<>();
        int paired = Math.min(expected.size(), actual.size());
        for (int i = 0; i < paired; i++) {
            differences.addAll(changed(expected.get(i), actual.get(i), chainIndex, spec));
        }
        for (int i = paired; i < expected.size(); i++) {
            differences.add(oneSided(expected.get(i), null, chainIndex, spec));
        }
        for (int i = paired; i < actual.size(); i++) {
            differences.add(oneSided(null, actual.get(i), chainIndex, spec));
        }
        expected.clear();
        actual.clear();
        return differences;
    }

    private List<FlowDiff.Difference> changed(
            FlowStep expected, FlowStep actual, int chainIndex, FlowDiff.DiffSpec spec) {
        if (expected.kind() != actual.kind()) {
            return List.of(oneSided(expected, null, chainIndex, spec),
                    oneSided(null, actual, chainIndex, spec));
        }
        return List.of(new FlowDiff.Difference(kindOfChange(expected, actual, spec),
                describe(expected, spec), describe(actual, spec),
                expected.lineNumber(), actual.lineNumber(), chainIndex, expected.depth()));
    }

    private FlowDiff.Difference.Kind kindOfChange(
            FlowStep expected, FlowStep actual, FlowDiff.DiffSpec spec) {
        switch (expected.kind()) {
            case CALL:
            case EVENT:
                if (Objects.equals(expected.to(), actual.to())
                        && Objects.equals(expected.label(), actual.label())) {
                    return FlowDiff.Difference.Kind.TIMING;
                }
                return Objects.equals(expected.to(), actual.to())
                        ? FlowDiff.Difference.Kind.DIFFERENT_SIGNATURE
                        : FlowDiff.Difference.Kind.DIFFERENT_TARGET;
            case RETURN:
            case THROW:
                if (!Objects.equals(expected.label(), actual.label())) {
                    return FlowDiff.Difference.Kind.DIFFERENT_RETURN;
                }
                // the returned type is the same, so what changed is who returned it:
                // the second half of a callee that differs, already reported on the call
                return Objects.equals(expected.from(), actual.from())
                        ? FlowDiff.Difference.Kind.TIMING
                        : FlowDiff.Difference.Kind.DIFFERENT_TARGET;
            case LOOP_START:
                return FlowDiff.Difference.Kind.LOOP_COUNT;
            case CHAIN_NOTE:
                return FlowDiff.Difference.Kind.UNEXPECTED_CHAIN;
            case TITLE:
                return FlowDiff.Difference.Kind.TITLE;
            case HOTSPOT:
                return FlowDiff.Difference.Kind.HOTSPOT;
            default:
                return FlowDiff.Difference.Kind.DIFFERENT_SIGNATURE;
        }
    }

    private FlowDiff.Difference oneSided(
            FlowStep expected, FlowStep actual, int chainIndex, FlowDiff.DiffSpec spec) {
        FlowStep present = expected == null ? actual : expected;
        FlowDiff.Difference.Kind kind = expected == null
                ? unexpectedKindOf(present) : missingKindOf(present);
        return new FlowDiff.Difference(kind,
                expected == null ? FlowDiff.Difference.MISSING : describe(expected, spec),
                actual == null ? FlowDiff.Difference.MISSING : describe(actual, spec),
                expected == null ? 0 : expected.lineNumber(),
                actual == null ? 0 : actual.lineNumber(),
                chainIndex, present.depth());
    }

    private FlowDiff.Difference.Kind missingKindOf(FlowStep step) {
        switch (step.kind()) {
            case PARTICIPANT:
                return FlowDiff.Difference.Kind.MISSING_PARTICIPANT;
            case CHAIN_NOTE:
            case CHAIN_START:
            case CHAIN_END:
                return FlowDiff.Difference.Kind.MISSING_CHAIN;
            case TITLE:
                return FlowDiff.Difference.Kind.TITLE;
            case HOTSPOT:
                return FlowDiff.Difference.Kind.HOTSPOT;
            default:
                return FlowDiff.Difference.Kind.MISSING_CALL;
        }
    }

    private FlowDiff.Difference.Kind unexpectedKindOf(FlowStep step) {
        switch (step.kind()) {
            case PARTICIPANT:
                return FlowDiff.Difference.Kind.UNEXPECTED_PARTICIPANT;
            case CHAIN_NOTE:
            case CHAIN_START:
            case CHAIN_END:
                return FlowDiff.Difference.Kind.UNEXPECTED_CHAIN;
            case TITLE:
                return FlowDiff.Difference.Kind.TITLE;
            case HOTSPOT:
                return FlowDiff.Difference.Kind.HOTSPOT;
            default:
                return FlowDiff.Difference.Kind.UNEXPECTED_CALL;
        }
    }

    private List<FlowDiff.Difference> mergeReorderings(List<FlowDiff.Difference> differences) {
        List<FlowDiff.Difference> merged = new ArrayList<>(differences);
        for (int i = 0; i < merged.size(); i++) {
            FlowDiff.Difference missing = merged.get(i);
            if (missing.kind() != FlowDiff.Difference.Kind.MISSING_CALL) {
                continue;
            }
            for (int j = 0; j < merged.size(); j++) {
                FlowDiff.Difference unexpected = merged.get(j);
                if (unexpected.kind() != FlowDiff.Difference.Kind.UNEXPECTED_CALL
                        || !missing.expected().equals(unexpected.actual())) {
                    continue;
                }
                merged.set(i, new FlowDiff.Difference(FlowDiff.Difference.Kind.WRONG_ORDER,
                        missing.expected(), unexpected.actual(),
                        missing.expectedLineNumber(), unexpected.actualLineNumber(),
                        missing.chainIndex(), missing.depth()));
                merged.remove(j);
                if (j < i) {
                    i--;
                }
                break;
            }
        }
        return merged;
    }

    private List<FlowDiff.Difference> compareTitles(
            List<FlowStep> expected, List<FlowStep> actual, FlowDiff.DiffSpec spec) {
        if (!spec.compareTitle()) {
            return List.of();
        }
        String expectedTitle = titleOf(expected);
        String actualTitle = titleOf(actual);
        if (expectedTitle.equals(actualTitle)) {
            return List.of();
        }
        return List.of(new FlowDiff.Difference(FlowDiff.Difference.Kind.TITLE,
                expectedTitle.isEmpty() ? FlowDiff.Difference.MISSING : "title " + expectedTitle,
                actualTitle.isEmpty() ? FlowDiff.Difference.MISSING : "title " + actualTitle,
                lineOfTitle(expected), lineOfTitle(actual), 0, 0));
    }

    private String titleOf(List<FlowStep> steps) {
        for (FlowStep step : steps) {
            if (step.kind() == FlowStep.Kind.TITLE) {
                return step.label();
            }
        }
        return "";
    }

    private int lineOfTitle(List<FlowStep> steps) {
        for (FlowStep step : steps) {
            if (step.kind() == FlowStep.Kind.TITLE) {
                return step.lineNumber();
            }
        }
        return 0;
    }

    private List<String> labelsOf(List<DiagramChain> chains) {
        List<String> labels = new ArrayList<>(chains.size());
        for (DiagramChain chain : chains) {
            labels.add(chain.label());
        }
        return labels;
    }

    private List<String> keysOf(List<FlowStep> steps, FlowDiff.DiffSpec spec) {
        List<String> keys = new ArrayList<>(steps.size());
        for (FlowStep step : steps) {
            keys.add(keyOf(step, spec));
        }
        return keys;
    }

    private String keyOf(FlowStep step, FlowDiff.DiffSpec spec) {
        StringBuilder key = new StringBuilder(step.kind().name())
                .append('|').append(step.from() == null ? "" : step.from())
                .append('|').append(step.to() == null ? "" : step.to())
                .append('|');
        if (step.kind() != FlowStep.Kind.LOOP_START || spec.compareLoopCounts()) {
            key.append(step.label());
        }
        if (spec.compareTimings()) {
            key.append('|').append(step.annotation());
        }
        return key.toString();
    }

    private String describe(FlowStep step, FlowDiff.DiffSpec spec) {
        String timing = spec.compareTimings() && !step.annotation().isEmpty()
                ? " [" + step.annotation() + "]" : "";
        switch (step.kind()) {
            case CALL:
            case EVENT:
            case RETURN:
            case THROW:
                return step.from() + " -> " + step.to() + ": " + step.label() + timing;
            case CHAIN_NOTE:
                return "chain " + step.label();
            case LOOP_START:
                return "loop " + step.label() + " times";
            case LOOP_END:
                return "end of loop";
            case CHAIN_START:
                return "start of chain";
            case CHAIN_END:
                return "end of chain";
            case PARTICIPANT:
                return "participant " + step.label();
            case TITLE:
                return "title " + step.label();
            default:
                return step.label();
        }
    }

    private List<FlowStep> significant(List<FlowStep> steps, FlowDiff.DiffSpec spec) {
        List<FlowStep> kept = new ArrayList<>(steps.size());
        for (FlowStep step : steps) {
            if (isComparable(step, spec)) {
                kept.add(step);
            }
        }
        return CallPatterns.applyIgnores(kept, spec);
    }

    private boolean isComparable(FlowStep step, FlowDiff.DiffSpec spec) {
        switch (step.kind()) {
            case HEADER:
            case HEADER_NOTE:
            case NOTE:
                return false;
            case TITLE:
                return spec.compareTitle();
            case HOTSPOT:
                return spec.compareHotspots();
            default:
                return true;
        }
    }
}
