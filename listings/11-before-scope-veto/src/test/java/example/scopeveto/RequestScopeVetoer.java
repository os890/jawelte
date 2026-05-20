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
package example.scopeveto;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.event.Observes;

import org.os890.jawelte.core.api.event.BeforeScopeStarted;

/**
 * Observes jawelte's {@link BeforeScopeStarted} event and vetoes
 * the {@code @RequestScoped} activation. cdi-module's
 * {@code CdiTestBeanContainer.beforeEach} returns early when
 * {@code event.isVetoed()} is true, so the
 * {@code RequestContextController} is never activated and never
 * bound on the {@code TestContext}. The {@link #VETOED} flag
 * mirrors the call so the test method can assert the observer
 * actually fired.
 */
@ApplicationScoped
public class RequestScopeVetoer {

    public static final AtomicBoolean VETOED = new AtomicBoolean(false);

    public void onBeforeScopeStarted(@Observes BeforeScopeStarted event) {
        if (event.getScope() == RequestScoped.class) {
            event.veto();
            VETOED.set(true);
        }
    }
}
