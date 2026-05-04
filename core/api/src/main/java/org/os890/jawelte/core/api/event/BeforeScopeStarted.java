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
package org.os890.jawelte.core.api.event;

import java.lang.annotation.Annotation;

/**
 * CDI event fired before a test scope (e.g. {@code @RequestScoped}) is
 * activated by a {@code TestModuleLifecyclePort}. Observers may call
 * {@link #veto()} to prevent activation; vetoed scopes stay inactive
 * for the duration of the test method, and beans in that scope throw
 * {@code ContextNotActiveException} on access. The test still runs.
 *
 * <p>{@code afterEach} / {@code afterAll} do NOT attempt to deactivate
 * a scope that was never activated.
 */
public class BeforeScopeStarted {

    private final Class<? extends Annotation> scope;
    private boolean vetoed;

    /**
     * Construct a {@code BeforeScopeStarted} event.
     *
     * @param scope the annotation type identifying the scope being
     *              started (e.g. {@code RequestScoped.class})
     */
    public BeforeScopeStarted(Class<? extends Annotation> scope) {
        this.scope = scope;
    }

    /**
     * Get the annotation type identifying the scope being started.
     *
     * @return the scope annotation type
     */
    public Class<? extends Annotation> getScope() {
        return scope;
    }

    /**
     * Veto activation of this scope. The scope will stay inactive for
     * the test method; beans in that scope throw
     * {@code ContextNotActiveException} on access.
     */
    public void veto() {
        this.vetoed = true;
    }

    /**
     * Whether {@link #veto()} has been called on this event.
     *
     * @return {@code true} if vetoed, {@code false} otherwise
     */
    public boolean isVetoed() {
        return vetoed;
    }
}
