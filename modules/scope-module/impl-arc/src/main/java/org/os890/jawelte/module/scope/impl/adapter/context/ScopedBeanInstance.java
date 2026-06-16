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
package org.os890.jawelte.module.scope.impl.adapter.context;

import jakarta.enterprise.context.spi.CreationalContext;

/**
 * Pairs the live bean reference with the {@code CreationalContext}
 * it was created from. The {@code CreationalContext} is needed when
 * the scope deactivates and calls {@code Contextual.destroy(instance,
 * creationalContext)} on each entry.
 *
 * <p>Used as the value type in the bean store's map; introspection
 * callers reaching the map via {@link ScopeStore#map()} only ever
 * need the {@link #instance()} accessor.
 *
 * @param <T>                the bean's instance type
 * @param instance           the live bean instance
 * @param creationalContext  the {@code CreationalContext} the bean
 *                           was created from
 */
public record ScopedBeanInstance<T>(T instance, CreationalContext<T> creationalContext) {
}
