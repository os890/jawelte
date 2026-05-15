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
package org.os890.jawelte.module.scope.impl.adapter.extension;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import jakarta.enterprise.context.NormalScope;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.inject.Scope;

import org.os890.jawelte.module.scope.api.AnnotationScopeRemap;

/**
 * Single CDI {@link Extension} that drives every
 * {@link AnnotationScopeRemap} provider on the classpath. Provider
 * discovery happens once at extension load time via
 * {@link ServiceLoader#load(Class)}; the resulting list is held
 * statically as an immutable copy.
 *
 * <p>For each type the CDI runtime delivers as a
 * {@link ProcessAnnotatedType} event the extension walks the
 * registered providers and, on the first whose
 * {@link AnnotationScopeRemap#trigger()} annotation is directly
 * present on the type, applies the remap:
 *
 * <ol>
 *   <li>If {@link AnnotationScopeRemap#preserveExplicitDirectScopes()}
 *       is {@code true} AND the type carries an explicit non-default
 *       CDI scope alongside the trigger, the remap is skipped — the
 *       user's choice is honoured.</li>
 *   <li>Otherwise every direct CDI scope annotation on the type is
 *       removed (so the bean does not end up with multiple scopes —
 *       a CDI deployment error) and the
 *       {@link AnnotationScopeRemap#targetScope()} annotation is
 *       added directly. Stereotype-contributed scopes (not directly
 *       declared) need no removal: the directly-added target wins
 *       per CDI's class-level-scope-wins-over-stereotype rule.</li>
 * </ol>
 *
 * <p>Annotation literals for the target scope are built dynamically
 * via {@link Proxy#newProxyInstance(ClassLoader, Class[], java.lang.reflect.InvocationHandler)}
 * and cached per scope class — no per-remap singleton literal class
 * is needed; the providers stay at four lines of declaration each.
 *
 * <p>Stateless — no instance fields beyond the static
 * {@link #REMAPS} and {@link #LITERAL_CACHE}. Discovered via
 * {@code META-INF/services/jakarta.enterprise.inject.spi.Extension}
 * alongside the other scope-module extensions.
 */
public class ScopeRemapCdiExtension implements Extension {

    private static final List<AnnotationScopeRemap> REMAPS = loadRemaps();

    private static final ConcurrentMap<Class<? extends Annotation>, Annotation> LITERAL_CACHE =
            new ConcurrentHashMap<>();

    /** No-arg constructor required by the CDI runtime. */
    public ScopeRemapCdiExtension() {
    }

    void onProcessAnnotatedType(@Observes ProcessAnnotatedType<?> event) {
        AnnotatedType<?> target = event.getAnnotatedType();
        for (AnnotationScopeRemap remap : REMAPS) {
            if (!target.isAnnotationPresent(remap.trigger())) {
                continue;
            }
            if (remap.preserveExplicitDirectScopes()
                    && hasExplicitOverrideScope(target, remap)) {
                return;
            }
            event.configureAnnotatedType()
                    .remove(annotation -> isCdiScope(annotation.annotationType()))
                    .add(literalFor(remap.targetScope()));
            return;
        }
    }

    private static boolean hasExplicitOverrideScope(
            AnnotatedType<?> target, AnnotationScopeRemap remap) {
        Class<? extends Annotation> triggerType = remap.trigger();
        Class<? extends Annotation> contributedScope = stereotypeContributedScope(triggerType);
        for (Annotation declared : target.getAnnotations()) {
            Class<? extends Annotation> declaredType = declared.annotationType();
            if (declaredType.equals(triggerType)) {
                continue;
            }
            if (contributedScope != null && declaredType.equals(contributedScope)) {
                continue;
            }
            if (isCdiScope(declaredType)) {
                return true;
            }
        }
        return false;
    }

    private static Class<? extends Annotation> stereotypeContributedScope(
            Class<? extends Annotation> trigger) {
        for (Annotation meta : trigger.getAnnotations()) {
            if (isCdiScope(meta.annotationType())) {
                return meta.annotationType();
            }
        }
        return null;
    }

    private static boolean isCdiScope(Class<? extends Annotation> annotationType) {
        return annotationType.isAnnotationPresent(NormalScope.class)
                || annotationType.isAnnotationPresent(Scope.class);
    }

    private static Annotation literalFor(Class<? extends Annotation> scope) {
        return LITERAL_CACHE.computeIfAbsent(scope, ScopeRemapCdiExtension::buildLiteral);
    }

    private static Annotation buildLiteral(Class<? extends Annotation> scope) {
        return (Annotation) Proxy.newProxyInstance(
                scope.getClassLoader(),
                new Class<?>[]{scope},
                (proxy, method, args) -> switch (method.getName()) {
                    case "annotationType" -> scope;
                    case "toString" -> "@" + scope.getName();
                    case "hashCode" -> 0;
                    case "equals" -> args != null
                            && args.length == 1
                            && args[0] instanceof Annotation other
                            && other.annotationType().equals(scope);
                    default -> method.getDefaultValue();
                });
    }

    private static List<AnnotationScopeRemap> loadRemaps() {
        List<AnnotationScopeRemap> list = new ArrayList<>();
        for (AnnotationScopeRemap remap : ServiceLoader.load(AnnotationScopeRemap.class)) {
            list.add(remap);
        }
        return List.copyOf(list);
    }
}
