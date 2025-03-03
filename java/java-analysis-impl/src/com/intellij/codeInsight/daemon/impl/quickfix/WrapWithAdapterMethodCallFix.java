// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

import static com.intellij.pom.java.LanguageLevel.JDK_11;
import static com.intellij.pom.java.LanguageLevel.JDK_1_9;

public final class WrapWithAdapterMethodCallFix extends LocalQuickFixAndIntentionActionOnPsiElement implements HighPriorityAction {
  static class Wrapper extends ArgumentFixerActionFactory {
    final Predicate<? super PsiType> myInTypeFilter;
    final Predicate<? super PsiType> myOutTypeFilter;
    final String myTemplate;

    /**
     * @param template      template for replacement (original expression is referenced as {@code {0}})
     * @param inTypeFilter  filter for input type (must return true if supplied type is acceptable as input type for this wrapper)
     * @param outTypeFilter quick filter for output type (must return true if supplied output type is acceptable for this wrapper).
     *                      It's allowed to check imprecisely (return true even if output type is not acceptable) as more
     *                      expensive type check will be performed automatically.
     */
    Wrapper(@NonNls String template, Predicate<? super PsiType> inTypeFilter, Predicate<? super PsiType> outTypeFilter) {
      myInTypeFilter = inTypeFilter;
      myOutTypeFilter = outTypeFilter;
      myTemplate = template;
    }

    boolean isApplicable(PsiElement context, PsiType inType, PsiType outType) {
      if (inType == null ||
          outType == null ||
          inType.equals(PsiType.NULL) ||
          !myInTypeFilter.test(inType) ||
          !myOutTypeFilter.test(outType)) {
        return false;
      }
      PsiType variableType = GenericsUtil.getVariableTypeByExpressionType(inType);
      if (LambdaUtil.notInferredType(variableType)) return false;
      if (variableType instanceof PsiDisjunctionType) {
        variableType = ((PsiDisjunctionType)variableType).getLeastUpperBound();
      }

      String typeText = variableType.getCanonicalText();
      // Empty text can be generated by PsiImmediateClassType if unresolved anonymous class is created like new X() {}
      if (typeText.isEmpty()) return false;
      PsiExpression replacement;
      try {
        replacement = createReplacement(context, "((" + typeText + ")null)");
      }
      catch (IncorrectOperationException ioe) {
        PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(variableType);
        String message = "Cannot create expression for type " + variableType.getClass() + "\n"
                         + "Canonical text: " + variableType.getCanonicalText() + "\n"
                         + "Internal text: " + variableType.getInternalCanonicalText() + "\n";
        if (aClass != null) {
          message += "Class: " + aClass.getClass() + "|" + aClass.getQualifiedName() + "\n"
                     + "File: " + aClass.getContainingFile() + "\n";
        }
        if (variableType instanceof PsiClassReferenceType) {
          PsiJavaCodeReferenceElement reference = ((PsiClassReferenceType)variableType).getReference();
          message += "Reference: " + reference.getCanonicalText() + "\n"
                     + "Reference class: " + reference.getClass() + "\n"
                     + "Reference name: " + reference.getReferenceName() + "\n"
                     + "Reference qualifier: " + (reference.getQualifier() == null ? "(null)" : reference.getQualifier().getText())
                     + "Reference file: " + reference.getContainingFile();
        }
        throw new IncorrectOperationException(message, (Throwable)ioe);
      }
      PsiDeclarationStatement declaration =
        JavaPsiFacade.getElementFactory(context.getProject()).createVariableDeclarationStatement("x", outType, replacement, context);
      PsiVariable var = ObjectUtils.tryCast(ArrayUtil.getFirstElement(declaration.getDeclaredElements()), PsiVariable.class);
      if (var == null) return false;
      PsiExpression initializer = var.getInitializer();
      if (initializer == null) return false;
      PsiType resultType = initializer.getType();
      return resultType != null && outType.isAssignableFrom(resultType);
    }

    @NotNull
    private PsiExpression createReplacement(PsiElement context, @NonNls String replacement) {
      return JavaPsiFacade.getElementFactory(context.getProject()).createExpressionFromText(
        myTemplate.replace("{0}", replacement), context);
    }

    @Nullable
    @Override
    protected PsiExpression getModifiedArgument(final PsiExpression expression, final PsiType toType) throws IncorrectOperationException {
      if (isApplicable(expression, expression.getType(), toType)) {
        return (PsiExpression)JavaCodeStyleManager.getInstance(expression.getProject())
          .shortenClassReferences(createReplacement(expression, expression.getText()));
      }
      return null;
    }

    @Override
    public boolean areTypesConvertible(@NotNull final PsiType exprType,
                                       @NotNull final PsiType parameterType,
                                       @NotNull final PsiElement context) {
      return parameterType.isConvertibleFrom(exprType) || isApplicable(context, exprType, parameterType);
    }

    @Override
    public MethodArgumentFix createFix(final PsiExpressionList list, final int i, final PsiType toType) {
      return new MyMethodArgumentFix(list, i, toType, this);
    }

    public String toString() {
      return myTemplate.replace("{0}", "").replaceAll("\\b[a-z.]+\\.", "");
    }
  }

  private static final Wrapper[] WRAPPERS = {
    new Wrapper("new java.io.File({0})",
                inType -> inType.equalsToText(CommonClassNames.JAVA_LANG_STRING),
                outType -> outType.equalsToText(CommonClassNames.JAVA_IO_FILE)),
    new Wrapper("new java.lang.StringBuilder({0})",
                inType -> inType.equalsToText(CommonClassNames.JAVA_LANG_STRING),
                outType -> outType.equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER)),
    new Wrapper("java.nio.file.Path.of({0})",
                inType -> inType.equalsToText(CommonClassNames.JAVA_LANG_STRING),
                outType -> outType.equalsToText("java.nio.file.Path") && isAppropriateLanguageLevel(outType, level -> level.isAtLeast(JDK_11))),
    new Wrapper("java.nio.file.Paths.get({0})",
                inType -> inType.equalsToText(CommonClassNames.JAVA_LANG_STRING),
                outType -> outType.equalsToText("java.nio.file.Path") && isAppropriateLanguageLevel(outType, level -> level.isLessThan(JDK_11))),
    new Wrapper("java.util.Arrays.asList({0})",
                inType -> inType instanceof PsiArrayType && ((PsiArrayType)inType).getComponentType() instanceof PsiClassType,
                outType -> InheritanceUtil.isInheritor(outType, CommonClassNames.JAVA_LANG_ITERABLE) &&
                           isAppropriateLanguageLevel(outType ,l -> l.isLessThan(JDK_1_9))),
    new Wrapper("java.util.List.of({0})",
                inType -> inType instanceof PsiArrayType && ((PsiArrayType)inType).getComponentType() instanceof PsiClassType,
                outType -> InheritanceUtil.isInheritor(outType, CommonClassNames.JAVA_LANG_ITERABLE) &&
                           isAppropriateLanguageLevel(outType ,l -> l.isAtLeast(JDK_1_9))),
    new Wrapper("java.lang.Math.toIntExact({0})",
                inType -> PsiType.LONG.equals(inType) || inType.equalsToText(CommonClassNames.JAVA_LANG_LONG),
                outType -> PsiType.INT.equals(outType) || outType.equalsToText(CommonClassNames.JAVA_LANG_INTEGER)),
    new Wrapper("java.util.Collections.singleton({0})",
                inType -> true,
                outType -> InheritanceUtil.isInheritor(outType, CommonClassNames.JAVA_LANG_ITERABLE)),
    new Wrapper("java.util.Collections.singletonList({0})",
                inType -> true,
                outType -> PsiTypesUtil.classNameEquals(outType, CommonClassNames.JAVA_UTIL_LIST)),
    new Wrapper("java.util.Arrays.stream({0})",
                inType -> inType instanceof PsiArrayType,
                outType -> InheritanceUtil.isInheritor(outType, CommonClassNames.JAVA_UTIL_STREAM_BASE_STREAM))
  };

  private static boolean isAppropriateLanguageLevel(@NotNull PsiType psiType, @NotNull Predicate<? super LanguageLevel> level) {
    if (!(psiType instanceof PsiClassType)) return true;
    return level.test(((PsiClassType)psiType).getLanguageLevel());
  }

  @SafeFieldForPreview
  @Nullable private final PsiType myType;
  @SafeFieldForPreview
  @Nullable private final Wrapper myWrapper;

  public WrapWithAdapterMethodCallFix(@Nullable PsiType type, @NotNull PsiExpression expression) {
    this(type, expression, ContainerUtil.find(WRAPPERS, w -> w.isApplicable(expression, expression.getType(), type)));
  }
  
  private WrapWithAdapterMethodCallFix(@Nullable PsiType type, @NotNull PsiExpression expression, @Nullable Wrapper wrapper) {
    super(expression);
    myType = type;
    myWrapper = wrapper;
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("wrap.with.adapter.text", myWrapper);
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("wrap.with.adapter.call.family.name");
  }


  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return myType != null && myWrapper != null && myType.isValid() && BaseIntentionAction.canModify(startElement);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(startElement.replace(getModifiedExpression(startElement)));
  }

  private PsiExpression getModifiedExpression(@NotNull PsiElement expression) {
    assert myWrapper != null;
    return myWrapper.createReplacement(expression, expression.getText());
  }

  private static class MyMethodArgumentFix extends MethodArgumentFix implements HighPriorityAction {

    protected MyMethodArgumentFix(@NotNull PsiExpressionList list, int i, @NotNull PsiType toType, @NotNull Wrapper fixerActionFactory) {
      super(list, i, toType, fixerActionFactory);
    }

    @Nls
    @NotNull
    @Override
    public String getText() {
      PsiExpressionList list = myArgList.getElement();
      return list != null && list.getExpressionCount() == 1
             ? QuickFixBundle.message("wrap.with.adapter.parameter.single.text", myArgumentFixerActionFactory)
             : QuickFixBundle.message("wrap.with.adapter.parameter.multiple.text", myIndex + 1, myArgumentFixerActionFactory);
    }

    @Override
    public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
      PsiExpressionList list = myArgList.getElement();
      if (list == null) return null;
      return new MyMethodArgumentFix(PsiTreeUtil.findSameElementInCopy(list, target), myIndex, myToType,
                                     (Wrapper)myArgumentFixerActionFactory);
    }
  }

  public static void registerCastActions(CandidateInfo @NotNull [] candidates,
                                         @NotNull PsiCall call,
                                         HighlightInfo highlightInfo,
                                         final TextRange fixRange) {
    for (Wrapper wrapper : WRAPPERS) {
      wrapper.registerCastActions(candidates, call, highlightInfo, fixRange);
    }
  }
}
