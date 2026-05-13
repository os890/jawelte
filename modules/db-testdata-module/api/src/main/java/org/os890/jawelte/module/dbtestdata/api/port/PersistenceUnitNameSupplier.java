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
package org.os890.jawelte.module.dbtestdata.api.port;

import org.os890.jawelte.module.dbtestdata.api.DbDiff;
import org.os890.jawelte.module.dbtestdata.api.DbSeed;
import org.os890.jawelte.module.jpa.api.PersistenceConfig;

/**
 * SPI port for the persistence-unit-name routing of
 * {@link DbSeed#forPersistenceUnit()} and
 * {@link DbDiff#forPersistenceUnit()}. Implementations are looked up
 * via CDI ({@code CDI.current().select(PersistenceUnitNameSupplier.class).get()}),
 * not ServiceLoader, so the lookup returns the active
 * {@code @ApplicationScoped} bean.
 *
 * <p>The default implementation shipped by db-testdata-module/impl is
 * a CDI bean populated by a CDI Extension that captures
 * {@link PersistenceConfig#persistenceUnitName()} from the active test
 * class during {@code BeforeBeanDiscovery} (when
 * {@code TestContext.get()} still resolves) and pushes it onto the
 * bean during the {@code @Initialized(ApplicationScoped.class)}
 * observer. Consumers can replace the default by providing their own
 * {@code @Alternative} CDI bean of this type.
 *
 * <p>Returning the empty string from {@link #get()} signals "no
 * annotation-driven routing", in which case
 * {@code forPersistenceUnit()} delegates to its
 * {@code forCurrentPersistenceUnit()} counterpart.
 */
public interface PersistenceUnitNameSupplier {

    /**
     * The persistence-unit name to route {@code forPersistenceUnit()}
     * to.
     *
     * @return the configured persistence-unit name; never
     *         {@code null}, possibly empty
     */
    String get();
}
