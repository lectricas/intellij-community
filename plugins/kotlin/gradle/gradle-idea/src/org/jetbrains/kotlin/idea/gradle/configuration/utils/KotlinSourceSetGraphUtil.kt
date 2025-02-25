// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

@file:Suppress("UnstableApiUsage")
package org.jetbrains.kotlin.idea.gradle.configuration.utils

import com.google.common.graph.*
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.kotlin.idea.gradleTooling.KotlinMPPGradleModel
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.projectModel.KotlinSourceSet

@IntellijInternalApi
fun createSourceSetVisibilityGraph(model: KotlinMPPGradleModel): ImmutableGraph<KotlinSourceSet> {
    val graph = createSourceSetDependsOnGraph(model)
    graph.putInferredTestToProductionEdges()
    return graph.immutable
}

internal fun createSourceSetDependsOnGraph(model: KotlinMPPGradleModel): MutableGraph<KotlinSourceSet> {
    return createSourceSetDependsOnGraph(model.sourceSetsByName)
}

@IntellijInternalApi
fun createSourceSetDependsOnGraph(
    sourceSetsByName: Map<String, KotlinSourceSet>
): MutableGraph<KotlinSourceSet> {
    val graph = GraphBuilder.directed().build<KotlinSourceSet>()
    val sourceSets = sourceSetsByName.values.toSet()

    for (sourceSet in sourceSets) {
        graph.addNode(sourceSet)
        val dependsOnSourceSets = getFixedDependsOnSourceSets(sourceSetsByName, sourceSet)
        for (dependsOnSourceSet in dependsOnSourceSets) {
            graph.addNode(dependsOnSourceSet)
            graph.putEdge(sourceSet, dependsOnSourceSet)
        }
    }

    return graph
}

@IntellijInternalApi
fun MutableGraph<KotlinSourceSet>.putInferredTestToProductionEdges() {
    val sourceSets = this.nodes()
    for (sourceSet in sourceSets) {
        if (sourceSet.isTestComponent) {
            @OptIn(UnsafeTestSourceSetHeuristicApi::class)
            val predictedMainSourceSetName = predictedProductionSourceSetName(sourceSet.name)
            val predictedMainSourceSet = sourceSets.firstOrNull { it.name == predictedMainSourceSetName } ?: continue
            putEdge(sourceSet, predictedMainSourceSet)
        }
    }
}

private fun getFixedDependsOnSourceSets(
    sourceSetsByName: Map<String, KotlinSourceSet>, sourceSet: KotlinSourceSet
): Set<KotlinSourceSet> {
    /*
    Workaround for older Kotlin Gradle Plugin versions that did not explicitly declare a dependsOn relation
    from a Kotlin source set to "commonMain"
    (Can probably be dropped in Kotlin 1.5)
     */
    val implicitDependsOnEdgeForAndroid = if (
        sourceSet.actualPlatforms.contains(KotlinPlatform.ANDROID) && sourceSet.declaredDependsOnSourceSets.isEmpty()
    ) {
        val commonSourceSetName = if (sourceSet.isTestComponent) KotlinSourceSet.COMMON_TEST_SOURCE_SET_NAME
        else KotlinSourceSet.COMMON_MAIN_SOURCE_SET_NAME
        listOfNotNull(sourceSetsByName[commonSourceSetName])
    } else emptyList()

    return sourceSet.declaredDependsOnSourceSets.map(sourceSetsByName::getValue)
        .plus(implicitDependsOnEdgeForAndroid)
        /*
        Gracefully filter out source sets that declare a dependency on themselves.
        This also fixes KTIJ-1025
        */
        .filter { dependencySourceSet -> dependencySourceSet != sourceSet }
        .toSet()
}

/**
 * @see Graphs.transitiveClosure
 */
@IntellijInternalApi
val <T> Graph<T>.transitiveClosure: Graph<T> get() = Graphs.transitiveClosure(this)

/**
 * @see ImmutableGraph.copyOf
 */
@IntellijInternalApi
val <T> Graph<T>.immutable: ImmutableGraph<T> get() = ImmutableGraph.copyOf(this)
