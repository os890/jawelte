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
package org.os890.jawelte.tests.scope.scenario06;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.enterprise.context.ContextNotActiveException;
import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestMethodScoped;

@EnableTestBeans
class Scenario06Test {

    @Inject
    Counter counter;

    @Test
    void vetoedTestMethodScopedStaysInactive() {
        // TestMethodScopedVetoer.on(BeforeScopeStarted) calls event.veto()
        // before scope-module's adapter would activate @TestMethodScoped.
        // scope-module honors the veto: the context is left inactive, so
        // accessing the @TestMethodScoped bean throws ContextNotActiveException.
        assertThatThrownBy(counter::increment)
                .isInstanceOf(ContextNotActiveException.class);
    }

    @AfterAll
    static void verifyVetoWasObservedDownstream() {
        // A second observer ordered after the vetoer sees the already-vetoed
        // event — confirms veto status propagates in the event chain (and, now
        // that the veto is honored, that scope-module acted on it above).
        assertThat(VetoObserver.SAW_VETOED_EVENT.get())
                .as("downstream observer must see isVetoed=true")
                .isTrue();
    }

    @TestMethodScoped
    public static class Counter {

        private int value;

        public void increment() {
            this.value++;
        }

        public int value() {
            return this.value;
        }
    }
}
