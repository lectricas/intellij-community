// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.jetbrains.kotlin.idea.test.TestRoot;
import org.junit.runner.RunWith;

/**
 * This class is generated by {@link org.jetbrains.kotlin.testGenerator.generator.TestGenerator}.
 * DO NOT MODIFY MANUALLY.
 */
@SuppressWarnings("all")
@TestRoot("gradle/gradle-java/tests")
@TestDataPath("$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
public abstract class GradleConfigureProjectByChangingFileTestGenerated extends AbstractGradleConfigureProjectByChangingFileTest {
    @RunWith(JUnit3RunnerWithInners.class)
    @TestMetadata("../../../idea/tests/testData/configuration/gradle")
    public static class Gradle extends AbstractGradleConfigureProjectByChangingFileTest {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTestGradle, this, testDataFilePath);
        }

        @TestMetadata("default")
        public void testDefault() throws Exception {
            runTest("../../../idea/tests/testData/configuration/gradle/default/");
        }

        @TestMetadata("eapVersion")
        public void testEapVersion() throws Exception {
            runTest("../../../idea/tests/testData/configuration/gradle/eapVersion/");
        }

        @TestMetadata("jreLib")
        public void testJreLib() throws Exception {
            runTest("../../../idea/tests/testData/configuration/gradle/jreLib/");
        }

        @TestMetadata("js")
        public void testJs() throws Exception {
            runTest("../../../idea/tests/testData/configuration/gradle/js/");
        }

        @TestMetadata("m04Version")
        public void testM04Version() throws Exception {
            runTest("../../../idea/tests/testData/configuration/gradle/m04Version/");
        }

        @TestMetadata("missedLibrary")
        public void testMissedLibrary() throws Exception {
            runTest("../../../idea/tests/testData/configuration/gradle/missedLibrary/");
        }

        @TestMetadata("plugin_present")
        public void testPlugin_present() throws Exception {
            runTest("../../../idea/tests/testData/configuration/gradle/plugin_present/");
        }

        @TestMetadata("rcVersion")
        public void testRcVersion() throws Exception {
            runTest("../../../idea/tests/testData/configuration/gradle/rcVersion/");
        }

        @TestMetadata("withJava9ModuleInfo")
        public void testWithJava9ModuleInfo() throws Exception {
            runTest("../../../idea/tests/testData/configuration/gradle/withJava9ModuleInfo/");
        }
    }

    @RunWith(JUnit3RunnerWithInners.class)
    @TestMetadata("../../../idea/tests/testData/configuration/gsk")
    public static class Gsk extends AbstractGradleConfigureProjectByChangingFileTest {
        private void runTest(String testDataFilePath) throws Exception {
            KotlinTestUtils.runTest(this::doTestGradle, this, testDataFilePath);
        }

        @TestMetadata("eap11Version")
        public void testEap11Version() throws Exception {
            runTest("../../../idea/tests/testData/configuration/gsk/eap11Version/");
        }

        @TestMetadata("eapVersion")
        public void testEapVersion() throws Exception {
            runTest("../../../idea/tests/testData/configuration/gsk/eapVersion/");
        }

        @TestMetadata("helloWorld")
        public void testHelloWorld() throws Exception {
            runTest("../../../idea/tests/testData/configuration/gsk/helloWorld/");
        }

        @TestMetadata("missedLibrary")
        public void testMissedLibrary() throws Exception {
            runTest("../../../idea/tests/testData/configuration/gsk/missedLibrary/");
        }

        @TestMetadata("pluginPresent")
        public void testPluginPresent() throws Exception {
            runTest("../../../idea/tests/testData/configuration/gsk/pluginPresent/");
        }
    }
}
