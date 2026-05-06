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
package org.os890.jawelte.core.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Stereotype;

/**
 * Marks a class as a config bean.
 *
 * <p>{@code @ConfigBean} is a CDI {@link Stereotype} that
 * meta-applies {@link ApplicationScoped}. A class annotated with
 * {@code @ConfigBean} is therefore a regular CDI bean with
 * application scope; the user does not need to declare
 * {@code @ApplicationScoped} explicitly.
 *
 * <p>The intended usage pattern is to inject
 * {@link org.os890.jawelte.core.api.port.ConfigResolver} into a
 * {@code @ConfigBean} class and expose one public method per config
 * key. Each method delegates the raw lookup to the resolver and is
 * responsible for parsing / converting the {@link String} value and
 * supplying a meaningful default.
 *
 * <p>The annotation defines no interceptors and no additional
 * behavior beyond the stereotype's scope contribution. It also
 * serves as a discovery marker so that, for example, the framework's
 * {@code limitToTestBeans} logic can identify config beans.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Stereotype
@ApplicationScoped
public @interface ConfigBean {
}
