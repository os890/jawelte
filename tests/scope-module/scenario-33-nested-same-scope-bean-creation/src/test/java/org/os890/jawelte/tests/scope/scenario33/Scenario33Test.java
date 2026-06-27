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
package org.os890.jawelte.tests.scope.scenario33;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestClassScoped;

/**
 * Scenario 33 — a {@code @TestClassScoped} bean whose own creation
 * triggers creation of OTHER {@code @TestClassScoped} beans in the
 * same scope. Each link {@code @Inject}s the next and dereferences it
 * in {@code @PostConstruct}, so resolving {@link Link0} drives a deep
 * chain of nested {@code Context.get()} → {@code Contextual.create()}
 * calls, all against the single per-test-class bean store.
 *
 * <p>This is the regression guard for the
 * {@code ConcurrentHashMap.computeIfAbsent} re-entrancy hazard: the
 * earlier context implementations created the bean INSIDE
 * {@code computeIfAbsent}'s mapping function, so a nested creation
 * re-entered the same map mid-computation — undefined behaviour that
 * throws {@code IllegalStateException("Recursive update")} whenever
 * two links land in the same hash bin. The fix serializes creation
 * per {@code Contextual} (see {@code ScopeStore.getOrCreate}) and runs
 * {@code create()} outside any map-structural lock, so the whole chain
 * assembles deterministically.
 *
 * <p>The chain is intentionally long enough that, under the old
 * {@code computeIfAbsent} approach, a same-bin collision among the
 * concurrently-locked links is overwhelmingly likely.
 */
@EnableTestBeans
class Scenario33Test {

    @Inject
    private Link0 root;

    @Test
    void nestedSameScopeCreationAssemblesTheWholeChain() {
        // Touching the root forces its creation, whose @PostConstruct
        // recursively forces creation of every downstream link.
        assertThat(root.chain())
                .as("every @TestClassScoped link must be created via nested creation, in order")
                .isEqualTo("0-1-2-3-4-5-6-7-8-9");
    }

    @Test
    void reResolutionReturnsTheSameChainInstances() {
        // No re-creation on subsequent access (beans live for the class).
        assertThat(root.chain()).isEqualTo("0-1-2-3-4-5-6-7-8-9");
    }

    // A chain of @TestClassScoped beans. Each link dereferences the
    // next in @PostConstruct, so creating Link0 nests creation of
    // Link1..Link9 within Link0's own in-progress creation.

    @TestClassScoped
    public static class Link0 {

        @Inject
        private Link1 next;

        private String chain;

        @PostConstruct
        void init() {
            chain = "0-" + next.chain();
        }

        String chain() {
            return chain;
        }
    }

    @TestClassScoped
    public static class Link1 {

        @Inject
        private Link2 next;

        private String chain;

        @PostConstruct
        void init() {
            chain = "1-" + next.chain();
        }

        String chain() {
            return chain;
        }
    }

    @TestClassScoped
    public static class Link2 {

        @Inject
        private Link3 next;

        private String chain;

        @PostConstruct
        void init() {
            chain = "2-" + next.chain();
        }

        String chain() {
            return chain;
        }
    }

    @TestClassScoped
    public static class Link3 {

        @Inject
        private Link4 next;

        private String chain;

        @PostConstruct
        void init() {
            chain = "3-" + next.chain();
        }

        String chain() {
            return chain;
        }
    }

    @TestClassScoped
    public static class Link4 {

        @Inject
        private Link5 next;

        private String chain;

        @PostConstruct
        void init() {
            chain = "4-" + next.chain();
        }

        String chain() {
            return chain;
        }
    }

    @TestClassScoped
    public static class Link5 {

        @Inject
        private Link6 next;

        private String chain;

        @PostConstruct
        void init() {
            chain = "5-" + next.chain();
        }

        String chain() {
            return chain;
        }
    }

    @TestClassScoped
    public static class Link6 {

        @Inject
        private Link7 next;

        private String chain;

        @PostConstruct
        void init() {
            chain = "6-" + next.chain();
        }

        String chain() {
            return chain;
        }
    }

    @TestClassScoped
    public static class Link7 {

        @Inject
        private Link8 next;

        private String chain;

        @PostConstruct
        void init() {
            chain = "7-" + next.chain();
        }

        String chain() {
            return chain;
        }
    }

    @TestClassScoped
    public static class Link8 {

        @Inject
        private Link9 next;

        private String chain;

        @PostConstruct
        void init() {
            chain = "8-" + next.chain();
        }

        String chain() {
            return chain;
        }
    }

    @TestClassScoped
    public static class Link9 {

        String chain() {
            return "9";
        }
    }
}
