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
package org.os890.jawelte.module.testcontrol.impl.adapter.observer;

import java.lang.annotation.Annotation;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.os890.jawelte.core.api.event.BeforeScopeStarted;

/**
 * {@code @ApplicationScoped} CDI bean shipped by
 * testcontrol-module/impl. Observes {@link BeforeScopeStarted} and
 * applies the per-method allow-list configured by
 * {@link org.os890.jawelte.module.testcontrol.impl.adapter.lifecycle.TestControlLifecycleAdapter}
 * from the active {@code @TestControl(startScopes=…)} value.
 *
 * <p><b>Allow-list state.</b> A {@code volatile} {@link Set} of scope
 * annotation classes set by {@link #configureAllowedScopes(Set)} —
 * {@code null} means "no filtering, all scope-module scopes activate
 * normally". A non-empty set means "veto every scope not in this
 * set". The {@code null} case covers both
 * {@code @TestControl(startScopes={})} (the annotation's sentinel for
 * "allow everything") and the absence of {@code @TestControl} on the
 * test method.
 *
 * <p>The adapter calls {@link #configureAllowedScopes(Set)} once per
 * {@code beforeEach}, before the scope-module participant fires its
 * own {@code BeforeScopeStarted} events. Because the adapter sits at
 * {@code @Priority(50)} and scope-module sits at {@code @Priority(100)},
 * the adapter's update has already taken effect by the time this
 * observer fires.
 *
 * <p><b>Scope of influence.</b> In practice only {@code @TestMethodScoped}
 * is affected — it is the one scope-module scope for which a
 * {@code BeforeScopeStarted} event is fired (per method, by
 * {@code ScopeLifecycleAdapter.beforeEach}). {@code @TestClassScoped}
 * has a class lifetime and emits <em>no</em> {@code BeforeScopeStarted}
 * event, so this observer never governs it — listing it in
 * {@code startScopes} has no effect.
 * Container-managed {@code @RequestScoped} <em>is</em> fired as a
 * {@code BeforeScopeStarted} event (by {@code CdiTestBeanContainer.beforeEach})
 * and so does reach this observer — but it stays active regardless of
 * {@code startScopes} for two reasons working together: the container
 * fires it <em>before</em> the {@code @Priority(50)} adapter applies
 * this method's allow-list (so the list is not yet active), and the
 * adapter clears the allow-list in {@code afterEach} so no previous
 * method's list lingers. With no allow-list in effect at that point,
 * {@link #onBeforeScopeStarted(BeforeScopeStarted)} returns early and
 * never vetoes {@code @RequestScoped}. scope-module's own events, by
 * contrast, fire at {@code @Priority(100)} — after the adapter has
 * applied the list — so they are the ones the allow-list governs.
 *
 * <p><b>Thread-safety.</b> {@code volatile} state plus a single-test
 * thread model. The per-method allow-list is not safe
 * for parallel test methods.
 */
@ApplicationScoped
public class TestControlScopeObserver {

    private volatile Set<Class<? extends Annotation>> allowedScopes;

    /** No-arg constructor required by the CDI runtime. */
    public TestControlScopeObserver() {
    }

    /**
     * Replace the allow-list. The adapter calls this in
     * {@code beforeEach}:
     *
     * <ul>
     *   <li>with the values from {@code @TestControl(startScopes=…)}
     *       when the annotation is present and {@code startScopes} is
     *       non-empty,</li>
     *   <li>with {@code null} (or an empty set) when the annotation
     *       is absent or {@code startScopes} is empty — both mean
     *       "no veto policy active".</li>
     * </ul>
     *
     * <p>A defensive copy is taken so the caller's collection is not
     * shared mutably with the observer.
     *
     * @param allowed the allowed scope annotation classes; {@code null}
     *                or empty clears any previous allow-list
     */
    public void configureAllowedScopes(Set<Class<? extends Annotation>> allowed) {
        if (allowed == null || allowed.isEmpty()) {
            this.allowedScopes = null;
        } else {
            this.allowedScopes = Set.copyOf(allowed);
        }
    }

    void onBeforeScopeStarted(@Observes BeforeScopeStarted event) {
        Set<Class<? extends Annotation>> active = allowedScopes;
        if (active == null) {
            return;
        }
        if (!active.contains(event.getScope())) {
            event.veto();
        }
    }
}
