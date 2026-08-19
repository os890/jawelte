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
package org.os890.jawelte.tests.cdi.scenario67;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.enterprise.util.Nonbinding;
import jakarta.inject.Qualifier;

/**
 * A qualifier with an array-valued member.
 *
 * <p>{@code note()} <em>must</em> carry {@code @Nonbinding}: CDI 4.1
 * makes an array-valued or annotation-valued qualifier member that does
 * not a definition error, and the container rejects the deployment
 * before any of jawelte's code runs. OpenWebBeans reports
 * {@code must have @NonBinding valued members for its array-valued and
 * annotation valued members}. So the only array member reachable here
 * is one that has to be ignored — which is exactly what this scenario
 * pins.
 *
 * <p>{@code channel()} is the binding member, and a scalar, because
 * that is the only kind a qualifier may bind on.
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD, ElementType.TYPE})
public @interface Tagged {

    /**
     * @return the binding channel
     */
    String channel();

    /**
     * @return a note that must not take part in qualifier equivalence
     */
    @Nonbinding
    String[] note() default {};
}
