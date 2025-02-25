// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.ValueParameterDescriptor
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.overrideImplement.BodyType
import org.jetbrains.kotlin.idea.core.overrideImplement.OverrideMemberChooserObject
import org.jetbrains.kotlin.idea.core.overrideImplement.generateMember
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.renderer.DescriptorRenderer
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

class SpecifyOverrideExplicitlyFix(
    element: KtClassOrObject, private val signature: String
) : KotlinQuickFixAction<KtClassOrObject>(element) {

    override fun getText() = KotlinBundle.message("specify.override.for.0.explicitly", signature)

    override fun getFamilyName() = KotlinBundle.message("specify.override.explicitly")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val context = element.analyzeWithContent()
        val delegatedDescriptor = context.diagnostics.forElement(element).mapNotNull {
            if (it.factory == Errors.DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE)
                Errors.DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE.cast(it).a
            else
                null
        }.firstOrNull {
            DescriptorRenderer.ONLY_NAMES_WITH_SHORT_TYPES.render(it) == signature
        } ?: return
        for (specifier in element.superTypeListEntries) {
            if (specifier is KtDelegatedSuperTypeEntry) {
                val superType = specifier.typeReference?.let { context[BindingContext.TYPE, it] } ?: continue
                val superTypeDescriptor = superType.constructor.declarationDescriptor as? ClassDescriptor ?: continue
                val overriddenDescriptor = delegatedDescriptor.overriddenDescriptors.find {
                    it.containingDeclaration == superTypeDescriptor
                } ?: continue

                val delegateExpression = specifier.delegateExpression as? KtNameReferenceExpression
                val delegateTargetDescriptor = context[BindingContext.REFERENCE_TARGET, delegateExpression] ?: return
                if (delegateTargetDescriptor is ValueParameterDescriptor &&
                    delegateTargetDescriptor.containingDeclaration.let {
                        it is ConstructorDescriptor &&
                                it.isPrimary &&
                                it.containingDeclaration == delegatedDescriptor.containingDeclaration
                    }
                ) {
                    val delegateParameter = DescriptorToSourceUtils.descriptorToDeclaration(
                        delegateTargetDescriptor
                    ) as? KtParameter
                    if (delegateParameter != null && !delegateParameter.hasValOrVar()) {
                        val factory = KtPsiFactory(project)
                        delegateParameter.addModifier(KtTokens.PRIVATE_KEYWORD)
                        delegateParameter.addAfter(factory.createValKeyword(), delegateParameter.modifierList)
                    }
                }

                val overrideMemberChooserObject = OverrideMemberChooserObject.create(
                    project, delegatedDescriptor, overriddenDescriptor,
                    BodyType.Delegate(delegateTargetDescriptor.name.asString())
                )
                val member = overrideMemberChooserObject.generateMember(element, copyDoc = false)
                val insertedMember = element.addDeclaration(member)
                ShortenReferences.DEFAULT.process(insertedMember)
                return
            }
        }

    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val hidesOverrideError = Errors.DELEGATED_MEMBER_HIDES_SUPERTYPE_OVERRIDE.cast(diagnostic)
            val klass = hidesOverrideError.psiElement
            if (klass.superTypeListEntries.any {
                    it is KtDelegatedSuperTypeEntry && it.delegateExpression !is KtNameReferenceExpression
                }) {
                return null
            }
            val properOverride = hidesOverrideError.a
            return SpecifyOverrideExplicitlyFix(klass, DescriptorRenderer.ONLY_NAMES_WITH_SHORT_TYPES.render(properOverride))
        }
    }
}