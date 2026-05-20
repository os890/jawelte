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
package example.scopeobserver;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.os890.jawelte.core.api.event.BeforeScopeStarted;

/**
 * Observes jawelte's {@link BeforeScopeStarted} event and logs the
 * scope that's about to be activated. The observer does NOT call
 * {@code event.veto()} — it just reacts; jawelte continues with the
 * activation. {@link BeforeScopeStarted#veto()} is still available
 * for observers that need to keep a scope inactive, but veto isn't
 * the common case and isn't what this listing demonstrates.
 *
 * <p>{@link #OBSERVED_SCOPES} mirrors every scope class the
 * observer saw, so the test can assert that {@code @RequestScoped}
 * came through.
 */
@ApplicationScoped
public class ScopeStartLogger {

    private static final Logger LOG = Logger.getLogger(ScopeStartLogger.class.getName());

    public static final List<Class<? extends Annotation>> OBSERVED_SCOPES = new CopyOnWriteArrayList<>();

    public void onBeforeScopeStarted(@Observes BeforeScopeStarted event) {
        LOG.info("scope about to start: " + event.getScope().getSimpleName());
        OBSERVED_SCOPES.add(event.getScope());
    }
}
