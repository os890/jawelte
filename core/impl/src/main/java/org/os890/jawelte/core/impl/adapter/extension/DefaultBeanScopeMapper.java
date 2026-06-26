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
package org.os890.jawelte.core.impl.adapter.extension;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.NormalScope;
import jakarta.inject.Scope;

import org.os890.jawelte.core.api.port.BeanScopeMapper;
import org.os890.jawelte.core.api.port.BeanScopeMapperPort;
import org.os890.jawelte.core.api.port.ServicePriorityResolver;
import org.os890.jawelte.core.api.port.TestContext;

/**
 * Default {@link BeanScopeMapperPort} implementation. Discovers
 * every {@link BeanScopeMapper} provider on the classpath via
 * {@link ServiceLoader} at construction time, orders them through
 * the active {@link ServicePriorityResolver} (lowest
 * {@code @Priority} first; providers without {@code @Priority}
 * sort last; ties broken by class name), and caches the resulting
 * immutable list; each {@link #mapScope(Class)} call walks that
 * ordered list and returns the first provider whose
 * {@link BeanScopeMapper#trigger() trigger} is present.
 *
 * <p>Ordering the inner provider list by priority — rather than
 * leaving it in raw {@code ServiceLoader} enumeration order — lets
 * a consumer override a built-in remap for a given trigger by
 * shipping a higher-precedence (lower-numeric {@code @Priority})
 * provider, consistent with every other multi-impl SPI in the
 * framework (e.g. {@code EjbAnnotationMapper}).
 *
 * <p>Port-level selection precedence (distinct from the provider
 * ordering above): this default is SL-registered at
 * {@link Integer#MAX_VALUE} priority so a customer
 * {@link BeanScopeMapperPort} registered with a lower (= higher
 * precedence) priority value wins the priority-resolution call in
 * {@code TestContext.loadService(BeanScopeMapperPort.class)} and
 * this default steps aside.
 *
 * <p>Stateless beyond the cached mapper list. No CDI types appear
 * on the public surface — the {@link ScopeRemapCdiExtension} wraps
 * this port and does the CDI-specific
 * {@code AnnotatedTypeConfigurator} dance.
 */
@Priority(Integer.MAX_VALUE)
public class DefaultBeanScopeMapper implements BeanScopeMapperPort {

    private final List<BeanScopeMapper> mappers;

    /** No-arg constructor required for SL discovery. */
    public DefaultBeanScopeMapper() {
        this.mappers = discoverMappers();
    }

    @Override
    public Optional<BeanScopeMapperPort.ScopeMappingMetadata> mapScope(Class<?> beanClass) {
        for (BeanScopeMapper mapper : mappers) {
            if (!beanClass.isAnnotationPresent(mapper.trigger())) {
                continue;
            }
            if (mapper.preserveExplicitDirectScopes()
                    && hasExplicitOverrideScope(beanClass, mapper)) {
                return Optional.empty();
            }
            Class<? extends Annotation> target = mapper.targetScope();
            if (target == null) {
                // Provider opted out for this lookup — typically
                // because its reflectively-loaded target class
                // wasn't on the runtime classpath. Skip and try
                // the next mapper.
                continue;
            }
            return Optional.of(new BeanScopeMapperPort.ScopeMappingMetadata(
                    target,
                    directCdiScopesOn(beanClass)));
        }
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> mapScope(Field testBeanField) {
        return targetScopeForElement(testBeanField);
    }

    @Override
    public Optional<Class<? extends Annotation>> mapScope(Method testBeanMethod) {
        return targetScopeForElement(testBeanMethod);
    }

    private Optional<Class<? extends Annotation>> targetScopeForElement(AnnotatedElement element) {
        for (BeanScopeMapper mapper : mappers) {
            if (!element.isAnnotationPresent(mapper.trigger())) {
                continue;
            }
            Class<? extends Annotation> target = mapper.targetScope();
            if (target == null) {
                continue;
            }
            return Optional.of(target);
        }
        return Optional.empty();
    }

    private static List<BeanScopeMapper> discoverMappers() {
        List<BeanScopeMapper> list = new ArrayList<>();
        for (BeanScopeMapper mapper : ServiceLoader.load(BeanScopeMapper.class)) {
            list.add(mapper);
        }
        // Order by @Priority through the project-wide resolver so a
        // higher-precedence provider can override a built-in remap for
        // a shared trigger — the same ordering every other multi-impl
        // SPI goes through. Without this the list would stay in raw
        // ServiceLoader/classpath order and the documented
        // "ship a higher-priority provider" override could not work.
        List<BeanScopeMapper> ordered = TestContext
                .loadService(ServicePriorityResolver.class)
                .sort(list);
        return List.copyOf(ordered);
    }

    private static Set<Class<? extends Annotation>> directCdiScopesOn(Class<?> beanClass) {
        Set<Class<? extends Annotation>> scopes = new HashSet<>();
        for (Annotation annotation : beanClass.getAnnotations()) {
            if (isCdiScope(annotation.annotationType())) {
                scopes.add(annotation.annotationType());
            }
        }
        return scopes;
    }

    private static boolean hasExplicitOverrideScope(Class<?> beanClass, BeanScopeMapper mapper) {
        Class<? extends Annotation> triggerType = mapper.trigger();
        Class<? extends Annotation> contributedScope = stereotypeContributedScope(triggerType);
        for (Annotation declared : beanClass.getAnnotations()) {
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
}
