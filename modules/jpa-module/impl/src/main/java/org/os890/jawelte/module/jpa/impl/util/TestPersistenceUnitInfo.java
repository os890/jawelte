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
package org.os890.jawelte.module.jpa.impl.util;

import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import jakarta.persistence.SharedCacheMode;
import jakarta.persistence.ValidationMode;
import jakarta.persistence.spi.ClassTransformer;
import jakarta.persistence.spi.PersistenceUnitInfo;
import jakarta.persistence.spi.PersistenceUnitTransactionType;

/**
 * Programmatic {@link PersistenceUnitInfo} used by jpa-module when
 * a {@code persistence.xml} has no {@code <class>} elements and
 * Hibernate's standard
 * {@code Persistence.createEntityManagerFactory(name, properties)}
 * path can't see the scanned entities (Hibernate doesn't auto-scan
 * outside an application server). The custom info forces
 * {@code excludeUnlistedClasses=true} and supplies the merged list
 * of declared + scanned entity FQCNs through
 * {@link #getManagedClassNames()}, which Hibernate honours.
 *
 * <p>Used together with
 * {@code HibernatePersistenceProvider.createContainerEntityManagerFactory(unitInfo, props)};
 * the standard JPA SPI accepts a custom
 * {@link PersistenceUnitInfo} for "container-managed" deployments,
 * which is exactly what jpa-module needs at test bootstrap.
 */
public class TestPersistenceUnitInfo implements PersistenceUnitInfo {

    private final String persistenceUnitName;

    private final List<String> managedClassNames;

    private final List<String> mappingFileNames;

    private final Properties properties;

    private final ClassLoader classLoader;

    private final URL persistenceUnitRootUrl;

    private final PersistenceUnitTransactionType transactionType;

    /**
     * Construct a programmatic {@link PersistenceUnitInfo}.
     *
     * @param persistenceUnitName the persistence unit name
     * @param managedClassNames   merged list of declared (from
     *                            {@code persistence.xml}'s {@code <class>}
     *                            entries) and scanned entity full
     *                            class names
     * @param mappingFileNames    mapping-file paths from
     *                            {@code persistence.xml}; may be empty
     * @param properties          merged properties bag (H2 overrides,
     *                            {@code hibernate.hbm2ddl.auto}, MP
     *                            Config overrides, …)
     * @param transactionType     RESOURCE_LOCAL or JTA
     */
    @SuppressWarnings("removal")
    public TestPersistenceUnitInfo(
            String persistenceUnitName,
            List<String> managedClassNames,
            List<String> mappingFileNames,
            Properties properties,
            PersistenceUnitTransactionType transactionType) {
        this.persistenceUnitName = persistenceUnitName;
        this.managedClassNames = List.copyOf(managedClassNames);
        this.mappingFileNames = List.copyOf(mappingFileNames);
        this.properties = properties;
        this.transactionType = transactionType;
        this.classLoader = Thread.currentThread().getContextClassLoader();
        this.persistenceUnitRootUrl = resolveRootUrl();
    }

    @Override
    public String getPersistenceUnitName() {
        return persistenceUnitName;
    }

    @Override
    public String getPersistenceProviderClassName() {
        return "org.hibernate.jpa.HibernatePersistenceProvider";
    }

    @SuppressWarnings("removal")
    @Override
    public PersistenceUnitTransactionType getTransactionType() {
        return transactionType;
    }

    @Override
    public DataSource getJtaDataSource() {
        return null;
    }

    @Override
    public DataSource getNonJtaDataSource() {
        return null;
    }

    @Override
    public List<String> getMappingFileNames() {
        return mappingFileNames;
    }

    @Override
    public List<URL> getJarFileUrls() {
        return Collections.emptyList();
    }

    @Override
    public URL getPersistenceUnitRootUrl() {
        return persistenceUnitRootUrl;
    }

    @Override
    public List<String> getManagedClassNames() {
        return managedClassNames;
    }

    @Override
    public boolean excludeUnlistedClasses() {
        return true;
    }

    @Override
    public SharedCacheMode getSharedCacheMode() {
        return SharedCacheMode.UNSPECIFIED;
    }

    @Override
    public ValidationMode getValidationMode() {
        return ValidationMode.AUTO;
    }

    @Override
    public Properties getProperties() {
        return properties;
    }

    @Override
    public String getPersistenceXMLSchemaVersion() {
        return "3.2";
    }

    @Override
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    @Override
    public void addTransformer(ClassTransformer transformer) {
        // no-op for tests; Hibernate's bytecode enhancement is opt-in
        // and not needed by jpa-module's smoke tests.
    }

    @Override
    public ClassLoader getNewTempClassLoader() {
        return classLoader;
    }

    @Override
    public String getScopeAnnotationName() {
        return null;
    }

    @Override
    public List<String> getQualifierAnnotationNames() {
        return Collections.emptyList();
    }

    private static URL resolveRootUrl() {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        URL classpathRootUrl = contextClassLoader.getResource("");
        if (classpathRootUrl != null) {
            return classpathRootUrl;
        }
        URL persistenceXmlUrl = contextClassLoader.getResource("META-INF/persistence.xml");
        if (persistenceXmlUrl == null) {
            return null;
        }
        String externalForm = persistenceXmlUrl.toExternalForm();
        int metaInfIndex = externalForm.indexOf("META-INF/persistence.xml");
        if (metaInfIndex <= 0) {
            return null;
        }
        try {
            return URI.create(externalForm.substring(0, metaInfIndex)).toURL();
        } catch (Exception ignored) {
            return null;
        }
    }
}
