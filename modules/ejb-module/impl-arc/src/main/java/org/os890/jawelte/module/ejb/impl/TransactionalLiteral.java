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
package org.os890.jawelte.module.ejb.impl;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.transaction.Transactional;

/**
 * Default-attribute {@link Transactional} literal supplying the
 * EJB-equivalent {@code REQUIRED} semantics with empty
 * {@code rollbackOn} / {@code dontRollbackOn} arrays.
 *
 * <p>{@code jakarta.transaction.Transactional} does not ship a
 * built-in {@code Literal.INSTANCE} (unlike the CDI scope annotations
 * in {@code jakarta.enterprise.cdi-api} 4.1+, which all do): the
 * annotation has attributes, so the API leaves the default-attribute
 * literal to the consumer. ejb-module supplies this one so the CDI
 * Extension can add a class-level {@code @Transactional} interceptor
 * binding to every EJB-mapped bean.
 *
 * <p>The literal is a singleton — {@link #INSTANCE} is the only
 * value the Extension hands to {@code addStereotype(...)} and
 * {@code configureAnnotatedType().add(...)}.
 */
class TransactionalLiteral extends AnnotationLiteral<Transactional> implements Transactional {

    /** Serial version for the {@code AnnotationLiteral} superclass. */
    private static final long serialVersionUID = 1L;

    /** Cached empty {@code Class[]} returned by {@link #rollbackOn()} and {@link #dontRollbackOn()}. */
    private static final Class<?>[] EMPTY_EXCEPTION_TYPES = new Class<?>[0];

    /** The singleton instance — every consumer reuses this. */
    static final TransactionalLiteral INSTANCE = new TransactionalLiteral();

    private TransactionalLiteral() {
    }

    @Override
    public TxType value() {
        return TxType.REQUIRED;
    }

    @Override
    public Class[] rollbackOn() {
        return EMPTY_EXCEPTION_TYPES;
    }

    @Override
    public Class[] dontRollbackOn() {
        return EMPTY_EXCEPTION_TYPES;
    }
}
