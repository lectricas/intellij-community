// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.caches.resolve

import com.intellij.facet.FacetManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiModificationTrackerImpl
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.analyzer.ModuleInfo
import org.jetbrains.kotlin.analyzer.ResolverForModuleComputationTracker
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.caches.project.ModuleSourceInfo
import org.jetbrains.kotlin.idea.caches.project.SdkInfo
import org.jetbrains.kotlin.idea.caches.trackers.KotlinCodeBlockModificationListener
import org.jetbrains.kotlin.idea.caches.trackers.KotlinModuleOutOfCodeBlockModificationTracker
import org.jetbrains.kotlin.idea.completion.test.withServiceRegistered
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.facet.KotlinFacetConfiguration
import org.jetbrains.kotlin.idea.facet.KotlinFacetType
import org.jetbrains.kotlin.idea.framework.JSLibraryKind
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.MockLibraryFacility
import org.jetbrains.kotlin.idea.test.allKotlinFiles
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.application.runWriteAction
import org.jetbrains.kotlin.idea.util.projectStructure.sdk
import org.jetbrains.kotlin.idea.util.sourceRoots
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverCommandLineProcessor.Companion.ANNOTATION_OPTION
import org.jetbrains.kotlin.samWithReceiver.SamWithReceiverCommandLineProcessor.Companion.PLUGIN_ID
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.junit.Assert.assertNotEquals
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.absoluteValue

@RunWith(JUnit38ClassRunner::class)
open class MultiModuleHighlightingTest : AbstractMultiModuleHighlightingTest() {
    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("multiModuleHighlighting")

    fun testVisibility() {
        val module1 = module("m1")
        val module2 = module("m2")

        module2.addDependency(module1)

        checkHighlightingInProject()
    }

    fun testDependency() {
        val module1 = module("m1")
        val module2 = module("m2")
        val module3 = module("m3")
        val module4 = module("m4")

        module2.addDependency(module1)

        module1.addDependency(module2)

        module3.addDependency(module2)

        module4.addDependency(module1)
        module4.addDependency(module2)
        module4.addDependency(module3)

        checkHighlightingInProject()
    }

    fun testLazyResolvers() {
        val tracker = ResolverTracker()

        project.withServiceRegistered<ResolverForModuleComputationTracker, Unit>(tracker) {
            val module1 = module("m1")
            val module2 = module("m2")
            val module3 = module("m3")

            module3.addDependency(module2)
            module3.addDependency(module1)

            assertTrue(module1 !in tracker.moduleResolversComputed)
            assertTrue(module2 !in tracker.moduleResolversComputed)
            assertTrue(module3 !in tracker.moduleResolversComputed)

            checkHighlightingInProject { project.allKotlinFiles().filter { "m3" in it.name } }

            assertTrue(module1 in tracker.moduleResolversComputed)
            assertTrue(module2 !in tracker.moduleResolversComputed)
            assertTrue(module3 in tracker.moduleResolversComputed)
        }
    }

    class ResolverTracker : ResolverForModuleComputationTracker {
        val moduleResolversComputed = mutableListOf<Module>()
        val sdkResolversComputed = mutableListOf<Sdk>()

        override fun onResolverComputed(moduleInfo: ModuleInfo) {
            when (moduleInfo) {
                is ModuleSourceInfo -> moduleResolversComputed.add(moduleInfo.module)
                is SdkInfo -> sdkResolversComputed.add(moduleInfo.sdk)
            }
        }
    }

    fun testRecomputeResolversOnChange() {
        val tracker = ResolverTracker()

        project.withServiceRegistered<ResolverForModuleComputationTracker, Unit>(tracker) {
            val module1 = module("m1")
            val module2 = module("m2")
            val module3 = module("m3")

            module2.addDependency(module1)
            module3.addDependency(module2)
            // Ensure modules have the same SDK instance, and not two distinct SDKs with the same path
            ModuleRootModificationUtil.setModuleSdk(module2, module1.sdk)

            assertEquals(0, tracker.sdkResolversComputed.size)

            checkHighlightingInProject { project.allKotlinFiles().filter { "m2" in it.name } }

            assertEquals(2, tracker.moduleResolversComputed.size)

            tracker.sdkResolversComputed.clear()
            tracker.moduleResolversComputed.clear()

            val module1ModTracker = KotlinModuleOutOfCodeBlockModificationTracker(module1)
            val module2ModTracker = KotlinModuleOutOfCodeBlockModificationTracker(module2)
            val module3ModTracker = KotlinModuleOutOfCodeBlockModificationTracker(module3)

            val m2ContentRoot = ModuleRootManager.getInstance(module1).contentRoots.single()
            val m1 = m2ContentRoot.findChild("m1.kt")!!
            val m1doc = FileDocumentManager.getInstance().getDocument(m1)!!
            project.executeWriteCommand("a") {
                m1doc.insertString(m1doc.textLength, "fun foo() = 1")
                PsiDocumentManager.getInstance(myProject).commitAllDocuments()
            }

            // Internal counters should be ready after modifications in m1
            val afterFirstModification = KotlinModuleOutOfCodeBlockModificationTracker.getModificationCount(module1)

            assertEquals(afterFirstModification, module1ModTracker.modificationCount)
            assertEquals(afterFirstModification, module2ModTracker.modificationCount)
            assertEquals(afterFirstModification, module3ModTracker.modificationCount)

            val m1ContentRoot = ModuleRootManager.getInstance(module2).contentRoots.single()
            val m2 = m1ContentRoot.findChild("m2.kt")!!
            val m2doc = FileDocumentManager.getInstance().getDocument(m2)!!
            project.executeWriteCommand("a") {
                m2doc.insertString(m2doc.textLength, "fun foo() = 1")
                PsiDocumentManager.getInstance(myProject).commitAllDocuments()
            }

            val currentModCount = KotlinCodeBlockModificationListener.getInstance(project).kotlinOutOfCodeBlockTracker.modificationCount

            // Counter for m1 module should be unaffected by modification in m2
            assertEquals(afterFirstModification, KotlinModuleOutOfCodeBlockModificationTracker.getModificationCount(module1))
            assertEquals(afterFirstModification, module1ModTracker.modificationCount)

            // Counters for m2 and m3 should be changed
            assertNotEquals(afterFirstModification, currentModCount)
            assertEquals(currentModCount, module2ModTracker.modificationCount)
            assertEquals(currentModCount, module3ModTracker.modificationCount)

            checkHighlightingInProject { project.allKotlinFiles().filter { "m2" in it.name } }

            assertEquals(0, tracker.sdkResolversComputed.size)
            assertEquals(2, tracker.moduleResolversComputed.size)

            tracker.moduleResolversComputed.clear()
            ApplicationManager.getApplication().runWriteAction {
                (PsiModificationTracker.SERVICE.getInstance(myProject) as PsiModificationTrackerImpl).incOutOfCodeBlockModificationCounter()
            }
            checkHighlightingInProject { project.allKotlinFiles().filter { "m2" in it.name } }
            assertEquals(0, tracker.sdkResolversComputed.size)
            assertEquals(2, tracker.moduleResolversComputed.size)
        }
    }

    fun testTestRoot() {
        val module1 = module("m1", hasTestRoot = true)
        val module2 = module("m2", hasTestRoot = true)
        val module3 = module("m3", hasTestRoot = true)

        module3.addDependency(module1, dependencyScope = DependencyScope.TEST)
        module3.addDependency(module2, dependencyScope = DependencyScope.TEST)
        module2.addDependency(module1, dependencyScope = DependencyScope.COMPILE)

        checkHighlightingInProject()
    }

    fun testLanguageVersionsViaFacets() {
        val m1 = module("m1").setupKotlinFacet {
            settings.languageLevel = LanguageVersion.KOTLIN_1_1
        }
        val m2 = module("m2").setupKotlinFacet {
            settings.languageLevel = LanguageVersion.KOTLIN_1_0
        }

        m1.addDependency(m2)
        m2.addDependency(m1)

        checkHighlightingInProject()
    }

    fun testSamWithReceiverExtension() {
        val module1 = module("m1").setupKotlinFacet {
            settings.compilerArguments!!.pluginOptions =
                arrayOf("plugin:$PLUGIN_ID:${ANNOTATION_OPTION.optionName}=anno.A")
        }

        val module2 = module("m2").setupKotlinFacet {
            settings.compilerArguments!!.pluginOptions =
                arrayOf("plugin:$PLUGIN_ID:${ANNOTATION_OPTION.optionName}=anno.B")
        }


        module1.addDependency(module2)
        module2.addDependency(module1)

        checkHighlightingInProject()
    }

    fun testResolutionAnchorsAndBuiltins() {
        val jarForCompositeLibrary = KotlinCompilerStandalone(
            sources = listOf(File("$testDataPath${getTestName(true)}/compositeLibraryPart"))
        ).compile()
        val stdlibJarForCompositeLibrary = KotlinArtifacts.instance.kotlinStdlib
        val jarForSourceDependentLibrary = KotlinCompilerStandalone(
            sources = listOf(File("$testDataPath${getTestName(true)}/sourceDependentLibrary"))
        ).compile()

        val dependencyModule = module("dependencyModule")
        val anchorModule = module("anchor")
        val sourceModule = module("sourceModule")

        val sourceDependentLibraryName = "sourceDependentLibrary"
        sourceModule.addMultiJarLibrary(listOf(stdlibJarForCompositeLibrary, jarForCompositeLibrary), "compositeLibrary")
        sourceModule.addLibrary(jarForSourceDependentLibrary, sourceDependentLibraryName)
        anchorModule.addDependency(dependencyModule)

        val anchorMapping = mapOf(sourceDependentLibraryName to anchorModule.name)

        withResolutionAnchors(anchorMapping) {
            checkHighlightingInProject()
            dependencyModule.modifyTheOnlySourceFile()
            checkHighlightingInProject()
        }
    }

    private fun Module.modifyTheOnlySourceFile() {
        val sourceRoot = sourceRoots.singleOrNull() ?: error("Expected single source root in a test module")
        assert(sourceRoot.isDirectory) { "Source root of a test module is not a directory" }
        val ktFile = sourceRoot.children.singleOrNull()?.toPsiFile(project) as? KtFile
            ?: error("Expected single .kt file in a test source module")

        val stubFunctionName = "fn${System.currentTimeMillis()}_${ThreadLocalRandom.current().nextInt().toLong().absoluteValue}"
        WriteCommandAction.runWriteCommandAction(project) {
            ktFile.add(
                KtPsiFactory(project).createFunction("fun $stubFunctionName() {}")
            )
        }
    }

    private fun Module.setupKotlinFacet(configure: KotlinFacetConfiguration.() -> Unit) = apply {
        runWriteAction {
            val facet = FacetManager.getInstance(this).addFacet(KotlinFacetType.INSTANCE, KotlinFacetType.NAME, null)
            val configuration = facet.configuration

            // this is actually needed so facet settings object is in a valid state
            configuration.settings.compilerArguments = K2JVMCompilerArguments()
            // make sure module-specific settings are used
            configuration.settings.useProjectSettings = false

            configuration.configure()
        }
    }

}
