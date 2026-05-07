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
package org.os890.jawelte.tests.scope.scenario15;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.module.scope.api.TestClassScoped;

@EnableTestBeans
public class Scenario15Subject {

    public static final List<String> EVENTS = new CopyOnWriteArrayList<>();

    @Inject
    Bean bean;

    public Scenario15Subject() {
    }

    @Test
    void touchTheBeanSoItGetsConstructed() {
        bean.touch();
    }

    @AfterAll
    static void recordAfterAll() {
        EVENTS.add("AFTER_ALL");
    }

    @TestClassScoped
    public static class Bean {

        public Bean() {
        }

        public void touch() {
        }

        @PreDestroy
        void onPreDestroy() {
            EVENTS.add("PRE_DESTROY");
        }
    }
}
