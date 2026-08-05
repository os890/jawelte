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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.os890.jawelte.module.flowassert.api.FlowDiff;
import org.os890.jawelte.module.flowassert.api.FlowStep;

/**
 * The ignore patterns of an assertion, applied to a parsed diagram.
 *
 * <p>A pattern is a glob over {@code Participant#signature} — the
 * receiving lane and the label of the call, e.g.
 * {@code AuditService#log(*)}, {@code AuditService#*} or
 * {@code *#log(String)}. Chain patterns are globs over the entry
 * point naming a block, e.g. {@code ShippingService.*}.
 *
 * <p>Dropping a call also drops its return, so the remaining sequence
 * stays balanced; {@code ignoringSubtree(...)} additionally drops
 * everything the call made happen. Both sides of a comparison are
 * filtered with the same patterns, so an ignored call may be present
 * in the expected file or absent from it.
 *
 * <p>{@code abstract} plus a private constructor per the project's
 * static-utility class convention.
 */
public abstract class CallPatterns {

    private CallPatterns() {
    }

    /**
     * Apply every ignore list of {@code spec} to {@code steps}.
     *
     * @param steps the parsed steps; must not be {@code null}
     * @param spec  the assertion's options; must not be {@code null}
     * @return the surviving steps, in document order; never {@code null}
     */
    public static List<FlowStep> applyIgnores(List<FlowStep> steps, FlowDiff.DiffSpec spec) {
        List<FlowStep> kept = withoutIgnoredChains(steps, spec.ignoreChainPatterns());
        kept = withoutSubtrees(kept, spec.ignoreSubtreePatterns());
        return withoutCalls(kept, spec.ignorePatterns());
    }

    /**
     * Whether {@code value} matches the glob {@code pattern}, with
     * {@code *} standing for any run of characters and everything else
     * matched literally.
     *
     * @param pattern the glob; must not be {@code null}
     * @param value   the value to test; must not be {@code null}
     * @return {@code true} on a match
     */
    public static boolean matches(String pattern, String value) {
        StringBuilder regex = new StringBuilder(pattern.length() * 2);
        for (char character : pattern.toCharArray()) {
            if (character == '*') {
                regex.append(".*");
            } else {
                regex.append(Pattern.quote(String.valueOf(character)));
            }
        }
        return value.matches(regex.toString());
    }

    private static List<FlowStep> withoutIgnoredChains(List<FlowStep> steps, List<String> patterns) {
        if (patterns.isEmpty()) {
            return steps;
        }
        Set<Integer> ignoredChains = new LinkedHashSet<>();
        for (FlowStep step : steps) {
            if (step.kind() == FlowStep.Kind.CHAIN_NOTE && matchesAny(patterns, step.label())) {
                ignoredChains.add(step.chainIndex());
            }
        }
        if (ignoredChains.isEmpty()) {
            return steps;
        }
        List<FlowStep> kept = new ArrayList<>(steps.size());
        for (FlowStep step : steps) {
            // a participant lane or the title belongs to the diagram rather than to
            // one of its chains, whatever chainIndex the parser stamped on it
            boolean diagramLevel = step.kind() == FlowStep.Kind.PARTICIPANT
                    || step.kind() == FlowStep.Kind.TITLE
                    || step.kind() == FlowStep.Kind.HEADER;
            if (diagramLevel || !ignoredChains.contains(step.chainIndex())) {
                kept.add(step);
            }
        }
        return kept;
    }

    private static List<FlowStep> withoutSubtrees(List<FlowStep> steps, List<String> patterns) {
        if (patterns.isEmpty()) {
            return steps;
        }
        List<FlowStep> kept = new ArrayList<>(steps.size());
        int index = 0;
        while (index < steps.size()) {
            FlowStep step = steps.get(index);
            if (isCall(step) && matchesAny(patterns, signatureOf(step))) {
                index = indexAfterReturnOf(steps, index);
                continue;
            }
            kept.add(step);
            index++;
        }
        return kept;
    }

    private static List<FlowStep> withoutCalls(List<FlowStep> steps, List<String> patterns) {
        if (patterns.isEmpty()) {
            return steps;
        }
        Set<Integer> dropped = new LinkedHashSet<>();
        for (int index = 0; index < steps.size(); index++) {
            FlowStep step = steps.get(index);
            if (!isCall(step) || !matchesAny(patterns, signatureOf(step))) {
                continue;
            }
            dropped.add(index);
            int afterReturn = indexAfterReturnOf(steps, index);
            if (afterReturn - 1 < steps.size()) {
                dropped.add(afterReturn - 1);
            }
        }
        List<FlowStep> kept = new ArrayList<>(steps.size());
        for (int index = 0; index < steps.size(); index++) {
            if (!dropped.contains(index)) {
                kept.add(steps.get(index));
            }
        }
        return kept;
    }

    /**
     * The index right after the return that closes the call at
     * {@code callIndex} — the first {@code RETURN} / {@code THROW} of
     * the same chain at the call's own depth. A recording that ends
     * mid-chain (an exception that escaped the recorder) has none, in
     * which case everything from the call onwards belongs to it.
     */
    private static int indexAfterReturnOf(List<FlowStep> steps, int callIndex) {
        FlowStep call = steps.get(callIndex);
        for (int index = callIndex + 1; index < steps.size(); index++) {
            FlowStep step = steps.get(index);
            boolean closing = step.kind() == FlowStep.Kind.RETURN || step.kind() == FlowStep.Kind.THROW;
            if (closing && step.depth() == call.depth() && step.chainIndex() == call.chainIndex()) {
                return index + 1;
            }
        }
        return steps.size();
    }

    private static boolean isCall(FlowStep step) {
        return step.kind() == FlowStep.Kind.CALL || step.kind() == FlowStep.Kind.EVENT;
    }

    private static String signatureOf(FlowStep step) {
        return step.to() + "#" + step.label();
    }

    private static boolean matchesAny(List<String> patterns, String value) {
        for (String pattern : patterns) {
            if (matches(pattern, value)) {
                return true;
            }
        }
        return false;
    }
}
