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
package org.os890.jawelte.tests.batch.scenario15;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.transaction.TransactionManager;

import org.jberet.repository.InMemoryRepository;
import org.jberet.repository.JobRepository;
import org.jberet.spi.ArtifactFactory;
import org.jberet.spi.BatchEnvironment;
import org.jberet.spi.JobTask;
import org.jberet.spi.JobXmlResolver;
import org.jberet.tools.MetaInfBatchJobsJobXmlResolver;

/**
 * Minimal CDI-runtime-agnostic {@link BatchEnvironment} for
 * scenario 15. Registered via
 * {@code META-INF/services/org.jberet.spi.BatchEnvironment}; the
 * static holder in JBeret's {@code BatchEnvironmentFactory}
 * picks it up the first time
 * {@code BatchRuntime.getJobOperator()} resolves a job operator
 * delegate.
 *
 * <p>Replaces JBeret's stock SE environment
 * ({@code org.jberet.se.BatchSEEnvironment}, whose
 * {@code SEArtifactFactory} hard-binds to
 * {@code org.jboss.weld.environment.se.WeldContainer}). The
 * substitute uses {@link TestArtifactFactory} (CDI portable
 * lookup), a {@link InMemoryRepository} (singleton from
 * jberet-core), a single cached thread pool for
 * {@link #submitTask(JobTask)}, and a no-op
 * {@link NoOpTransactionManager} since this scenario's batchlet
 * never opens a transaction.
 */
public class TestBatchEnvironment implements BatchEnvironment {

    private static final AtomicInteger THREAD_INDEX = new AtomicInteger();

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "scenario-15-batch-" + THREAD_INDEX.getAndIncrement());
        t.setDaemon(true);
        return t;
    });

    private final ArtifactFactory artifactFactory = new TestArtifactFactory();

    private final JobXmlResolver jobXmlResolver = new MetaInfBatchJobsJobXmlResolver();

    private final TransactionManager transactionManager = new NoOpTransactionManager();

    /** No-arg constructor required by {@link java.util.ServiceLoader}. */
    public TestBatchEnvironment() {
    }

    @Override
    public ClassLoader getClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl != null ? cl : TestBatchEnvironment.class.getClassLoader();
    }

    @Override
    public ArtifactFactory getArtifactFactory() {
        return artifactFactory;
    }

    @Override
    public void submitTask(JobTask task) {
        executor.submit(task);
    }

    @Override
    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    @Override
    public JobRepository getJobRepository() {
        return InMemoryRepository.getInstance();
    }

    @Override
    public JobXmlResolver getJobXmlResolver() {
        return jobXmlResolver;
    }

    @Override
    public Properties getBatchConfigurationProperties() {
        return new Properties();
    }

    @Override
    public String getApplicationName() {
        return "scenario-15";
    }
}
