// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.StandardPatterns
import com.intellij.util.ProcessingContext
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.core.ExpectedInfo
import org.jetbrains.kotlin.idea.kdoc.getKDocLinkMemberScope
import org.jetbrains.kotlin.idea.kdoc.getKDocLinkResolutionScope
import org.jetbrains.kotlin.idea.kdoc.getParamDescriptors
import org.jetbrains.kotlin.idea.kdoc.resolveKDocLink
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocLink
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.utils.collectDescriptorsFiltered
import java.util.*

class KDocCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC, psiElement().inside(KDocName::class.java),
            KDocNameCompletionProvider
        )

        extend(
            CompletionType.BASIC,
            psiElement().afterLeaf(
                StandardPatterns.or(psiElement(KDocTokens.LEADING_ASTERISK), psiElement(KDocTokens.START))
            ),
            KDocTagCompletionProvider
        )

        extend(
            CompletionType.BASIC,
            psiElement(KDocTokens.TAG_NAME), KDocTagCompletionProvider
        )
    }
}

object KDocNameCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        KDocNameCompletionSession(parameters, result).complete()
    }
}

class KDocNameCompletionSession(
    parameters: CompletionParameters,
    resultSet: CompletionResultSet
) : CompletionSession(CompletionSessionConfiguration(parameters), parameters, resultSet) {

    override val descriptorKindFilter: DescriptorKindFilter? get() = null
    override val expectedInfos: Collection<ExpectedInfo> get() = emptyList()

    override fun doComplete() {
        val position = parameters.position.getParentOfType<KDocName>(false) ?: return
        val declaration = position.getContainingDoc().getOwner() ?: return
        val kdocLink = position.getStrictParentOfType<KDocLink>()!!
        val declarationDescriptor = bindingContext[BindingContext.DECLARATION_TO_DESCRIPTOR, declaration] ?: return
        if (kdocLink.getTagIfSubject()?.knownTag == KDocKnownTag.PARAM) {
            addParamCompletions(position, declarationDescriptor)
        } else {
            addLinkCompletions(declarationDescriptor, kdocLink)
        }
    }


    private fun addParamCompletions(
        position: KDocName,
        declarationDescriptor: DeclarationDescriptor
    ) {
        val section = position.getContainingSection()
        val documentedParameters = section.findTagsByName("param").map { it.getSubjectName() }.toSet()
        getParamDescriptors(declarationDescriptor)
            .filter { it.name.asString() !in documentedParameters }
            .forEach {
                collector.addElement(basicLookupElementFactory.createLookupElement(it, parametersAndTypeGrayed = true))
            }
    }

    private fun collectDescriptorsForLinkCompletion(
        declarationDescriptor: DeclarationDescriptor,
        kDocLink: KDocLink
    ): Collection<DeclarationDescriptor> {
        val contextScope = getKDocLinkResolutionScope(resolutionFacade, declarationDescriptor)

        val qualifiedLink = kDocLink.getLinkText().split('.').dropLast(1)
        val nameFilter = descriptorNameFilter.toNameFilter()
        return if (qualifiedLink.isNotEmpty()) {
            val parentDescriptors =
                resolveKDocLink(bindingContext, resolutionFacade, declarationDescriptor, kDocLink.getTagIfSubject(), qualifiedLink)
            parentDescriptors.flatMap {
                val scope = getKDocLinkMemberScope(it, contextScope)
                scope.getContributedDescriptors(nameFilter = nameFilter)
            }
        } else {
            contextScope.collectDescriptorsFiltered(DescriptorKindFilter.ALL, nameFilter, changeNamesForAliased = true)
        }
    }

    private fun addLinkCompletions(declarationDescriptor: DeclarationDescriptor, kDocLink: KDocLink) {
        collectDescriptorsForLinkCompletion(declarationDescriptor, kDocLink).forEach {
            val element = basicLookupElementFactory.createLookupElement(it, parametersAndTypeGrayed = true)
            collector.addElement(
                // insert only plain name here, no qualifier/parentheses/etc.
                LookupElementDecorator.withDelegateInsertHandler(element, EmptyDeclarativeInsertHandler)
            )
        }
    }
}

object KDocTagCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        // findIdentifierPrefix() requires identifier part characters to be a superset of identifier start characters
        val prefix = CompletionUtil.findIdentifierPrefix(
            parameters.position.containingFile,
            parameters.offset,
            StandardPatterns.character().javaIdentifierPart() or singleCharPattern('@'),
            StandardPatterns.character().javaIdentifierStart() or singleCharPattern('@')
        )

        if (parameters.isAutoPopup && prefix.isEmpty()) return
        if (prefix.isNotEmpty() && !prefix.startsWith('@')) {
            return
        }
        val kdocOwner = parameters.position.getNonStrictParentOfType<KDoc>()?.getOwner()
        val resultWithPrefix = result.withPrefixMatcher(prefix)
        KDocKnownTag.values().forEach {
            if (kdocOwner == null || it.isApplicable(kdocOwner)) {
                resultWithPrefix.addElement(LookupElementBuilder.create("@" + it.name.toLowerCase(Locale.US)))
            }
        }
    }

    private fun KDocKnownTag.isApplicable(declaration: KtDeclaration) = when (this) {
        KDocKnownTag.CONSTRUCTOR, KDocKnownTag.PROPERTY -> declaration is KtClassOrObject
        KDocKnownTag.RETURN -> declaration is KtNamedFunction
        KDocKnownTag.RECEIVER -> declaration is KtNamedFunction && declaration.receiverTypeReference != null
        else -> true
    }
}
