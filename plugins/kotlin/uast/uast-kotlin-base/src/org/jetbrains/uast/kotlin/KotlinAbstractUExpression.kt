// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.uast.kotlin

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.convertOpt

abstract class KotlinAbstractUExpression(
    givenParent: UElement?,
) : KotlinAbstractUElement(givenParent), UExpression {

    override val javaPsi: PsiElement? = null

    override val psi
        get() = sourcePsi

    override val uAnnotations: List<UAnnotation>
        get() {
            val annotatedExpression = sourcePsi?.parent as? KtAnnotatedExpression ?: return emptyList()
            return annotatedExpression.annotationEntries.mapNotNull { languagePlugin?.convertOpt(it, this) }
        }
}
