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
package org.os890.jawelte.module.ejb.api.port;

import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * Pluggable discovery of the EJB session-bean annotated types
 * ejb-module registers as CDI beans. The active impl is resolved
 * through {@code TestContext.loadService(EjbAnnotationScanner.class)};
 * ejb-module/impl ships {@code XbeanFinderEjbAnnotationScanner} as the
 * default at {@code @Priority(Integer.MAX_VALUE)} (the lowest-priority
 * fallback per the project's resolution rule). Consumers that need a
 * different discovery model — a build-time index, a restricted
 * classpath, a fixed hand-written list — register an alternative impl
 * at a lower {@code @Priority} via {@code META-INF/services}.
 *
 * <p>The scan runs during {@code BeforeBeanDiscovery}, the earliest
 * CDI lifecycle event, so an impl must not rely on CDI being usable:
 * no {@code BeanManager} lookups, no injection, no
 * {@code CDI.current()}.
 *
 * <p>Because the classpath walk is the expensive part and is
 * invariant for a given classloader and configuration, impls are
 * expected to cache. The shipped default keeps its result per
 * classloader, so a suite of many test classes pays the walk once
 * rather than once per container boot.
 */
public interface EjbAnnotationScanner {

    /**
     * Find the types that ejb-module should add as annotated types.
     *
     * <p>The returned set contains every type on the active classpath
     * that carries at least one of
     * {@code beanDefiningAnnotations}, minus:
     *
     * <ul>
     *   <li>types whose fully-qualified name starts with one of
     *       {@code excludedPackagePrefixes}; and</li>
     *   <li>types that already carry a normal scope or
     *       {@code @Dependent}. Those are discoverable under
     *       {@code bean-discovery-mode="annotated"} through the
     *       standard CDI rules already, and adding them a second time
     *       produces a duplicate bean — OpenWebBeans rejects that with
     *       {@code DuplicateDefinitionException}. Pseudo-scopes such as
     *       {@code @jakarta.inject.Singleton} do <em>not</em> count:
     *       they are not bean-defining per the CDI 4.0 spec.</li>
     * </ul>
     *
     * <p>Both filters belong to the contract rather than the caller so
     * that an impl is free to cache the finished result.
     *
     * @param beanDefiningAnnotations the class-level annotations to
     *                                look for; never {@code null}, and
     *                                an empty set yields an empty
     *                                result
     * @param excludedPackagePrefixes package-name prefixes to drop;
     *                                never {@code null}
     * @return the matching types, in a stable iteration order; never
     *         {@code null}
     */
    Set<Class<?>> scan(Set<Class<? extends Annotation>> beanDefiningAnnotations,
                       Set<String> excludedPackagePrefixes);
}
