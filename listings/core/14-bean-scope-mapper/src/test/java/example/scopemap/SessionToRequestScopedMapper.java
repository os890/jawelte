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
package example.scopemap;

import java.lang.annotation.Annotation;

import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.context.SessionScoped;

import org.os890.jawelte.core.api.port.BeanScopeMapper;

/**
 * Custom {@code BeanScopeMapper} provider. Every bean directly
 * annotated {@link SessionScoped @SessionScoped} has its scope
 * rewritten to {@link RequestScoped @RequestScoped} at
 * {@code ProcessAnnotatedType} time — production keeps the web-tier
 * scope, the test runtime gets a scope it can actually activate.
 *
 * <p>ServiceLoader-registered via {@code
 * META-INF/services/org.os890.jawelte.core.api.port.BeanScopeMapper}.
 */
public class SessionToRequestScopedMapper implements BeanScopeMapper {

    @Override
    public Class<? extends Annotation> trigger() {
        return SessionScoped.class;
    }

    @Override
    public Class<? extends Annotation> targetScope() {
        return RequestScoped.class;
    }
}
