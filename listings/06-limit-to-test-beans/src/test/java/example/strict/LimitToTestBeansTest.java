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
package example.strict;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;
import org.os890.jawelte.core.api.EnableTestBeans;
import org.os890.jawelte.core.api.TestBean;

@EnableTestBeans(limitToTestBeans = true)
@TestBean(bean = StubEmailService.class)
class LimitToTestBeansTest {

    @Inject
    BeanManager beanManager;

    @Inject
    EmailService emailService;

    @Test
    void onlyDeclaredBeansSurvive() {
        assertThat(emailService.send("alice@example.com")).isEqualTo("stub:alice@example.com");

        // Production beans NOT named in a @TestBean declaration are
        // vetoed at discovery time. SmtpEmailService (the production
        // EmailService impl) and AuditService (an unrelated
        // @ApplicationScoped bean) are absent from the container.
        assertThat(beanManager.getBeans(SmtpEmailService.class)).isEmpty();
        assertThat(beanManager.getBeans(AuditService.class)).isEmpty();
    }
}
