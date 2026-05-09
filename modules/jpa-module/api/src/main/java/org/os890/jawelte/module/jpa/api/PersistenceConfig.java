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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Per-test-class JPA configuration. Placed on a test class to switch
 * the H2 connection mode used by jpa-module's
 * {@code persistence.xml} override and to filter the active set of
 * persistence units.
 *
 * <p>Without this annotation the framework behaves as
 * {@code fileMode=false}, {@code filePath=""}, and
 * {@code persistenceUnits={}}: every persistence unit declared in
 * {@code persistence.xml} is bootstrapped against the in-memory H2
 * database {@code jdbc:h2:mem:{puName};DB_CLOSE_DELAY=-1}.
 *
 * <p>Inherited so a base test class can establish the configuration
 * for a whole hierarchy.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface PersistenceConfig {

    /**
     * Whether the database should run in H2 file mode. When
     * {@code true}, every persistence unit on the test class connects
     * to {@code jdbc:h2:file:{filePath}/{puName}} instead of the
     * in-memory URL, and per-method table cleanup is skipped (files
     * persist across JVM restarts). When {@code false}, the in-memory
     * H2 URL is used and per-method cleanup runs.
     *
     * @return {@code true} for file-mode, {@code false} for in-memory
     */
    boolean fileMode() default false;

    /**
     * Directory used for H2 files when {@code fileMode=true}. When
     * empty (the default), the path defaults to
     * {@code ~/{appLabel}_db/}, where {@code appLabel} comes from the
     * MicroProfile Config key {@code org.os890.jawelte.module.jpa.app-label}
     * (falling back to the test class's simple name when unset).
     * Ignored when {@code fileMode=false}.
     *
     * @return the directory path or empty string for the default
     */
    String filePath() default "";

    /**
     * Filter on the set of persistence units to bootstrap. When
     * non-empty, only persistence units whose name appears in the
     * array are bootstrapped; other persistence units in
     * {@code persistence.xml} are ignored. When empty (the default),
     * every persistence unit declared in {@code persistence.xml} is
     * bootstrapped.
     *
     * @return persistence-unit names to include or an empty array to
     *         include every persistence unit
     */
    String[] persistenceUnits() default {};

    /**
     * The persistence-unit name the active {@link
     * org.os890.jawelte.module.jpa.api.port.TransactionStrategy}
     * should eagerly open on {@code begin()} when more than one
     * persistence unit is active. Empty (the default) means the
     * strategy resolves the eager PU itself by walking the
     * intercepted bean / test class for a single
     * {@code @Inject @Named EntityManager} (or
     * {@code @PersistenceContext(unitName=...)}) field; if zero or
     * more-than-one distinct names are found the strategy starts no
     * transaction up-front and lets every PU lazy-join on first
     * {@code EntityManager} dereference. Setting this attribute is
     * the explicit override — useful when the test reaches multiple
     * persistence units but one of them is the natural "primary"
     * scope. Ignored when only one persistence unit is active.
     *
     * @return the eager-managed persistence-unit name, or an empty
     *         string to let the strategy decide
     */
    String persistenceUnitName() default "";
}
