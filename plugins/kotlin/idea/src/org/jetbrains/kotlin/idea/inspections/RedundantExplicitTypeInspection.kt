// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.intentions.RemoveExplicitTypeIntention
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.isCompanionObject
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.AbbreviatedType
import org.jetbrains.kotlin.types.KotlinType

class RedundantExplicitTypeInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
        propertyVisitor(fun(property) {
            val typeReference = property.typeReference ?: return
            if (hasRedundantType(property)) {
                holder.registerProblem(
                    typeReference,
                    KotlinBundle.message("explicitly.given.type.is.redundant.here"),
                    ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                    IntentionWrapper(RemoveExplicitTypeIntention())
                )
            }
        })

    companion object {
        fun hasRedundantType(property: KtProperty): Boolean {
            if (!property.isLocal) return false
            val typeReference = property.typeReference ?: return false
            if (typeReference.annotationEntries.isNotEmpty()) return false
            val initializer = property.initializer ?: return false

            val type = property.resolveToDescriptorIfAny()?.type ?: return false
            if (type is AbbreviatedType) return false
            when (initializer) {
                is KtConstantExpression -> {
                    when (initializer.node.elementType) {
                        KtNodeTypes.BOOLEAN_CONSTANT -> {
                            if (!KotlinBuiltIns.isBoolean(type)) return false
                        }
                        KtNodeTypes.INTEGER_CONSTANT -> {
                            if (initializer.text.endsWith("L")) {
                                if (!KotlinBuiltIns.isLong(type)) return false
                            } else {
                                if (!KotlinBuiltIns.isInt(type)) return false
                            }
                        }
                        KtNodeTypes.FLOAT_CONSTANT -> {
                            if (initializer.text.endsWith("f") || initializer.text.endsWith("F")) {
                                if (!KotlinBuiltIns.isFloat(type)) return false
                            } else {
                                if (!KotlinBuiltIns.isDouble(type)) return false
                            }
                        }
                        KtNodeTypes.CHARACTER_CONSTANT -> {
                            if (!KotlinBuiltIns.isChar(type)) return false
                        }
                        else -> return false
                    }
                }
                is KtStringTemplateExpression -> {
                    if (!KotlinBuiltIns.isString(type)) return false
                }
                is KtNameReferenceExpression -> {
                    if (typeReference.text != initializer.getReferencedName()) return false
                    val initializerType = initializer.getType(property.analyze(BodyResolveMode.PARTIAL))
                    if (initializerType != type && initializerType.isCompanionObject()) return false
                }
                is KtCallExpression -> {
                    if (typeReference.text != initializer.calleeExpression?.text) return false
                }
                else -> return false
            }
            return true
        }

        private fun KotlinType?.isCompanionObject() =
            this?.constructor?.declarationDescriptor?.isCompanionObject() == true
    }
}