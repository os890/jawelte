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
package org.os890.jawelte.module.jpa.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.interceptor.InterceptorBinding;

/**
 * Modifier for a {@code jakarta.transaction.Transactional} method or
 * type that prevents writes from being persisted. Combined with
 * {@code @Transactional}, jpa-module's interceptor switches the active
 * {@code EntityManager}'s flush mode to {@code COMMIT} (so dirty
 * checks do not auto-flush) and marks the transaction rollback-only
 * before completion. Net effect: any {@code em.persist(...)} calls in
 * the annotated method or type are discarded.
 *
 * <p>Without {@code @Transactional} on the same level,
 * {@code @ReadOnly} has no effect — it is an
 * {@code @InterceptorBinding} and no interceptor fires unless the
 * Transactional binding is also present. This is documented behaviour,
 * not an error; no warning is logged.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@InterceptorBinding
public @interface ReadOnly {
}
