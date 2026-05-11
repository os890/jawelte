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
import java.util.List;

import jakarta.enterprise.inject.spi.BeanManager;

/**
 * Pluggable mapping from EJB session-bean annotations to CDI scopes
 * plus interceptor bindings. ejb-module/impl ships the default mapper
 * ({@code DefaultEjbAnnotationMapper}) that covers
 * {@code @jakarta.ejb.Singleton} and {@code @jakarta.ejb.Stateless};
 * a custom impl can take over for any class — to support
 * {@code @Stateful}, {@code @MessageDriven}, or to override the
 * default mapping for one of the standard annotations.
 *
 * <p>The CDI Extension in ejb-module/impl resolves the mapper chain
 * once during {@code BeforeBeanDiscovery} by enumerating candidates
 * via {@code ServiceLoader.load(EjbAnnotationMapper.class)} and
 * sorting them through the project-wide
 * {@code ServicePriorityResolver}. During {@code ProcessAnnotatedType}
 * the extension walks the sorted chain in priority order: the first
 * additional mapper (i.e. {@link #isAdditionalMapper()} {@code true})
 * that returns a non-{@code null} result claims the class; the
 * terminal default ({@code isAdditionalMapper() == false}) runs only
 * when every additional mapper returned {@code null}.
 *
 * <p>Implementations register via
 * {@code META-INF/services/org.os890.jawelte.module.ejb.api.port.EjbAnnotationMapper}
 * and carry a {@code jakarta.annotation.Priority} for ordering (lowest
 * value wins; full class names break ties).
 */
public interface EjbAnnotationMapper {

    /**
     * Whether this mapper supplements the default (an <em>additional</em>
     * mapper) or replaces it (a <em>terminal</em> mapper).
     *
     * <p>Additional mappers run first in priority order; the first
     * one to return a non-{@code null} result claims the class. The
     * terminal mapper runs only when every additional mapper returned
     * {@code null}. Exactly one terminal mapper is expected per
     * classpath — ejb-module/impl ships
     * {@code DefaultEjbAnnotationMapper} as that terminal.
     *
     * @return {@code true} for additional mappers (default),
     *         {@code false} for the terminal default
     */
    default boolean isAdditionalMapper() {
        return true;
    }

    /**
     * Compute the CDI annotations to add to {@code beanClass}. Three
     * meanings of the result:
     *
     * <ul>
     *   <li>{@code null} — the mapper does not handle this class.
     *       Control falls through to the next mapper in priority
     *       order (or to the terminal default if every additional
     *       mapper returned {@code null}).</li>
     *   <li>An <strong>empty</strong> list — the mapper claims the
     *       class but contributes no annotations. The default is
     *       <em>not</em> consulted as a fallback; the bean is left
     *       unchanged.</li>
     *   <li>A <strong>non-empty</strong> list — the mapper claims
     *       the class. The CDI Extension applies every element via
     *       {@code configureAnnotatedType().add(...)}; the default
     *       is skipped for this class.</li>
     * </ul>
     *
     * <p>The terminal default mapper SHOULD detect a user-declared
     * CDI scope on {@code beanClass} (via a meta-annotation pass for
     * {@code @NormalScope} or {@code @Scope}) and omit a redundant
     * scope from the returned list — see the EJB-to-CDI scope
     * precedence rules in the ejb-module specification.
     *
     * <p>Implementations must not throw — a thrown exception aborts
     * CDI bootstrap. The CDI Extension does not catch
     * {@code RuntimeException} from mappers; surfacing the failure
     * is the right behaviour.
     *
     * @param beanClass   the type being processed; never {@code null}
     * @param beanManager the in-flight CDI {@code BeanManager} that
     *                    bootstrapping observers receive; never
     *                    {@code null}
     * @return the annotations to add (possibly empty), or {@code null}
     *         to defer to the next mapper
     */
    List<Annotation> mapBeanMetadata(Class<?> beanClass, BeanManager beanManager);
}
