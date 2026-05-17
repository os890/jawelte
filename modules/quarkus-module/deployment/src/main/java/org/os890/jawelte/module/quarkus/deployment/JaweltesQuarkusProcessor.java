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
package org.os890.jawelte.module.quarkus.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Build-time processor for the jawelte Quarkus extension. Hosts the
 * {@code @BuildStep} methods that replace cdi-module's portable
 * {@code TestBeansCdiExtension} under {@code @QuarkusTest}: discovery of
 * {@code @EnableTestBeans} configuration, resolution of the
 * {@code @TestBean} alternative set, registration of synthetic mock
 * beans for unsatisfied injection points, and annotation
 * transformations that add {@code @Priority} plus a fallback
 * {@code @Singleton} to selected alternatives.
 *
 * <p>For now the processor exposes only the {@link #feature()}
 * build-step so Quarkus's build pipeline recognises the extension and
 * surfaces it in {@code quarkus:list-extensions}. The build-time
 * machinery for {@code @TestBean} discovery and auto-mock synthesis
 * lands in follow-up commits.
 */
public class JaweltesQuarkusProcessor {

    private static final String FEATURE = "jawelte-quarkus";

    /** Default constructor used by the Quarkus build framework. */
    public JaweltesQuarkusProcessor() {
    }

    /**
     * Registers the extension's feature label so the build report
     * shows {@code jawelte-quarkus} as one of the active features.
     *
     * @return the feature build item identifying this extension
     */
    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
