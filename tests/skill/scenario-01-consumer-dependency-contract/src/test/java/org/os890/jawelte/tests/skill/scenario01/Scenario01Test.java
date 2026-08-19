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
package org.os890.jawelte.tests.skill.scenario01;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class Scenario01Test {

    private static final Path ROOT = RepositoryLayout.root();

    private static Map<String, String> parentManagedScopes() {
        return RepositoryLayout.managedScopes(RepositoryLayout.read(ROOT.resolve("pom.xml")));
    }

    private static List<String> dependenciesOf(String modulePath) {
        return RepositoryLayout.declaredDependencies(RepositoryLayout.read(ROOT.resolve(modulePath)));
    }

    private static String effectiveScopeIn(String modulePath, String artifactId) {
        return RepositoryLayout.effectiveScope(
                RepositoryLayout.read(ROOT.resolve(modulePath)),
                RepositoryLayout.read(ROOT.resolve("pom.xml")),
                artifactId);
    }

    @Test
    void theApisAConsumerMustDeclareAreProvidedAndThereforeNotTransitive() {
        Map<String, String> managed = parentManagedScopes();

        assertThat(managed)
                .as("setup.md tells a consumer to declare these itself; that is only true while "
                        + "jawelte-parent manages them at provided scope, which Maven does not "
                        + "propagate to a consuming project")
                .containsEntry("jakarta.enterprise.cdi-api", "provided")
                .containsEntry("jakarta.annotation-api", "provided")
                .containsEntry("jakarta.inject-api", "provided")
                .containsEntry("jakarta.persistence-api", "provided")
                .containsEntry("jakarta.transaction-api", "provided")
                .containsEntry("microprofile-config-api", "provided")
                .containsEntry("mockito-core", "provided");
    }

    @Test
    void theJawelteArtifactsThemselvesAreCompileScopedAndThereforeTransitive() {
        Map<String, String> managed = parentManagedScopes();

        assertThat(managed)
                .as("depending on an -impl has to bring its own -api and jawelte-core-api along, "
                        + "or the minimal pom in setup.md would not resolve")
                .containsEntry("jawelte-core-api", "compile")
                .containsEntry("jawelte-cdi-module-api", "compile")
                .containsEntry("jawelte-cdi-module-impl", "compile")
                .containsEntry("jawelte-core-impl", "compile");
    }

    @Test
    void cdiModuleImplDoesNotDependOnCoreImplSoAConsumerMustAddItExplicitly() {
        List<String> dependencies = dependenciesOf("modules/cdi-module/impl/pom.xml");

        assertThat(dependencies)
                .as("the whole reason setup.md lists jawelte-core-impl as a second required "
                        + "dependency: cdi-module/impl reaches core only through TestContext's "
                        + "SPI lookup, so nothing pulls core/impl onto a consumer's classpath")
                .doesNotContain("jawelte-core-impl")
                .contains("jawelte-cdi-module-api", "jawelte-core-api");
    }

    @Test
    void modulesThatNeedTheNamingTreeAtRuntimeDependOnItsImplThemselves() {
        assertThat(dependenciesOf("modules/datasource-module/impl/pom.xml"))
                .as("datasource-module binds into the naming tree, so it carries jndi-module/impl")
                .contains("jawelte-jndi-module-impl");

        assertThat(dependenciesOf("modules/jta-module/impl/pom.xml"))
                .as("jta-module binds into the naming tree too")
                .contains("jawelte-jndi-module-impl");
    }

    @Test
    void modulesThatOnlyReadThePortDependOnTheApiSoTheConsumerSuppliesTheImpl() {
        assertThat(dependenciesOf("modules/jpa-module/impl/pom.xml"))
                .as("setup.md tells a jpa consumer to add jndi-module/impl; that instruction "
                        + "exists precisely because jpa-module/impl declares only the api")
                .contains("jawelte-jndi-module-api")
                .doesNotContain("jawelte-jndi-module-impl");

        assertThat(dependenciesOf("modules/testcontrol-module/impl/pom.xml"))
                .as("same shape for testcontrol on top of db-testdata")
                .contains("jawelte-db-testdata-module-api")
                .doesNotContain("jawelte-db-testdata-module-impl");
    }

    @Test
    void theThirdPartyLibrariesSetupMdCallsTransitiveArriveAtCompileScope() {
        assertThat(effectiveScopeIn("modules/db-testdata-module/impl/pom.xml", "dbunit"))
                .as("setup.md's 'comes transitively' column - a consumer is told NOT to declare "
                        + "DBUnit, so a switch away from compile would break that instruction")
                .isEqualTo("compile");

        assertThat(effectiveScopeIn("modules/wiremock-module/impl/pom.xml", "wiremock"))
                .isEqualTo("compile");

        assertThat(effectiveScopeIn("modules/content-diff-module/impl/pom.xml", "jackson-databind"))
                .isEqualTo("compile");

        assertThat(effectiveScopeIn("modules/flow-assert-module/impl/pom.xml", "dynamic-cdi-flow-renderer"))
                .as("the cdi-flow recorder is the one third-party library flow-assert consumers "
                        + "explicitly do not have to declare")
                .isEqualTo("compile");
    }

    @Test
    void theLibrariesSetupMdCallsConsumerSuppliedAreNotTransitive() {
        assertThat(effectiveScopeIn("modules/jndi-module/impl/pom.xml", "xbean-naming"))
                .as("managed at test scope by the parent and overridden to provided by the module "
                        + "that needs it; either way Maven does not propagate it, which is why "
                        + "setup.md lists it as consumer-supplied")
                .isEqualTo("provided");

        assertThat(effectiveScopeIn("modules/jpa-module/impl/pom.xml", "hibernate-core"))
                .isEqualTo("provided");

        assertThat(effectiveScopeIn("modules/ejb-module/impl/pom.xml", "jakarta.ejb-api"))
                .isEqualTo("provided");

        assertThat(effectiveScopeIn("modules/spring-data-module/pom.xml", "spring-data-jpa"))
                .as("spring-data-module inherits the parent's provided scope rather than "
                        + "overriding it, so a Spring Data consumer supplies the library itself")
                .isEqualTo("provided");
    }
}
