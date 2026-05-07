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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Bytecode-level scanner that walks the JVM's classpath, inspects
 * every {@code .class} file via ASM, and returns the full class
 * names of types annotated with {@code @jakarta.persistence.Entity}.
 * Avoids {@link Class#forName(String)} so static initialisers and
 * native library loads do not run for non-test code.
 *
 * <p>Honours a configurable list of excluded package prefixes so
 * the user can keep sensitive or expensive packages out of the
 * scan.
 *
 * <p>The classpath is read from the {@code java.class.path} system
 * property, split on {@link File#pathSeparator}. Each entry is
 * either a directory (walked recursively) or a {@code .jar} file
 * (iterated via {@link ZipFile}). Read errors are logged at
 * {@link Level#WARNING} and skipped — a failed entry never breaks
 * the scan.
 */
public abstract class EntityScanner {

    private static final Logger LOG = System.getLogger(EntityScanner.class.getName());

    private static final String ENTITY_DESCRIPTOR = "Ljakarta/persistence/Entity;";

    /**
     * Suppressed-instantiation constructor. The class is
     * {@code abstract} so direct {@code new} is impossible; the
     * explicit declaration silences {@code javadoc -doclint:all} on
     * the otherwise synthesized default constructor.
     */
    protected EntityScanner() {
    }

    /**
     * Scan the JVM classpath for {@code @Entity}-annotated types.
     *
     * @param excludedPackagePrefixes package prefixes (e.g.
     *                                {@code "java."}, {@code "org.junit."})
     *                                whose types are skipped; may be
     *                                empty but not {@code null}
     * @return the full class names of every {@code @Entity}-annotated
     *         type found on the classpath, in classpath traversal
     *         order; never {@code null}
     */
    public static Set<String> scan(Set<String> excludedPackagePrefixes) {
        Set<String> entities = new LinkedHashSet<>();
        String classpath = System.getProperty("java.class.path", "");
        for (String entry : classpath.split(File.pathSeparator)) {
            if (entry.isEmpty()) {
                continue;
            }
            scanClasspathEntry(entry, excludedPackagePrefixes, entities);
        }
        return Collections.unmodifiableSet(entities);
    }

    private static void scanClasspathEntry(String entry, Set<String> excludes, Set<String> entities) {
        Path path = Paths.get(entry);
        if (!Files.exists(path)) {
            return;
        }
        try {
            if (Files.isDirectory(path)) {
                scanDirectory(path, excludes, entities);
            } else if (entry.toLowerCase().endsWith(".jar")) {
                scanJar(path, excludes, entities);
            }
        } catch (IOException io) {
            LOG.log(Level.WARNING, "Skipping classpath entry '" + entry + "' due to I/O error", io);
        }
    }

    private static void scanDirectory(Path root, Set<String> excludes, Set<String> entities) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".class"))
                    .forEach(p -> readClassFile(p, excludes, entities));
        }
    }

    private static void scanJar(Path jar, Set<String> excludes, Set<String> entities) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entriesEnum = zip.entries();
            while (entriesEnum.hasMoreElements()) {
                ZipEntry zipEntry = entriesEnum.nextElement();
                if (zipEntry.isDirectory() || !zipEntry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream stream = zip.getInputStream(zipEntry)) {
                    inspectClassBytes(stream, excludes, entities);
                } catch (IOException ignored) {
                    LOG.log(Level.WARNING,
                            "Skipping jar entry '" + zipEntry.getName() + "' in " + jar);
                }
            }
        }
    }

    private static void readClassFile(Path classFile, Set<String> excludes, Set<String> entities) {
        try (InputStream stream = Files.newInputStream(classFile)) {
            inspectClassBytes(stream, excludes, entities);
        } catch (IOException ignored) {
            LOG.log(Level.WARNING, "Skipping class file '" + classFile + "'");
        }
    }

    private static void inspectClassBytes(InputStream stream, Set<String> excludes, Set<String> entities)
            throws IOException {
        ClassReader reader = new ClassReader(stream);
        String internalName = reader.getClassName();
        String fullClassName = internalName.replace('/', '.');
        if (matchesExclude(fullClassName, excludes)) {
            return;
        }
        EntityCheckVisitor visitor = new EntityCheckVisitor();
        reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (visitor.isEntity) {
            entities.add(fullClassName);
        }
    }

    private static boolean matchesExclude(String className, Set<String> excludes) {
        for (String prefix : excludes) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ASM visitor that flags whether the class carries the
     * {@code @jakarta.persistence.Entity} annotation. Stops at the
     * class header so field / method bodies are not visited.
     */
    private static class EntityCheckVisitor extends ClassVisitor {

        private boolean isEntity;

        EntityCheckVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (ENTITY_DESCRIPTOR.equals(descriptor)) {
                this.isEntity = true;
            }
            return null;
        }
    }
}
