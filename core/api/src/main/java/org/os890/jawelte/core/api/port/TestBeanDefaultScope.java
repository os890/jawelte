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
package org.os890.jawelte.core.api.port;

import java.lang.annotation.Annotation;

/**
 * Cross-module override for the default CDI scope of a {@code @TestBean}
 * static-field synthetic bean. Bound on {@link TestContext} via
 * {@link TestContext#bindMetadata(Class, Object)} by feature modules
 * that ship longer-lived test scopes (e.g. scope-module's
 * {@code @TestClassScoped}); read by cdi-module's CDI Extension during
 * {@code AfterBeanDiscovery} when registering the synthetic bean.
 *
 * <p>Precedence (highest first): a CDI scope annotation declared by the
 * test author on the static field; this metadata record (when bound);
 * cdi-module's own {@code @Singleton} fallback (when the record is
 * unbound).
 *
 * <p>Lives in {@code core/api/port} so cdi-module and the override
 * provider communicate without compile-time coupling — neither side
 * pulls the other in.
 *
 * @param scope the CDI scope annotation type to use as the default
 *              for {@code @TestBean} static-field synthetic beans
 */
public record TestBeanDefaultScope(Class<? extends Annotation> scope) {
}
