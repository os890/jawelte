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
package example.difffeatures;

/**
 * Plain Java bean exposed to Jakarta EL via
 * {@code DbDiff.withBean("catalog", new Catalog())}. The expected
 * dataset references {@code ${catalog.appleName}} /
 * {@code ${catalog.bananaName}} and EL property navigation resolves
 * those to the getter return values before the diff runs.
 */
public class Catalog {

    public String getAppleName() {
        return "apple";
    }

    public String getBananaName() {
        return "banana";
    }
}
