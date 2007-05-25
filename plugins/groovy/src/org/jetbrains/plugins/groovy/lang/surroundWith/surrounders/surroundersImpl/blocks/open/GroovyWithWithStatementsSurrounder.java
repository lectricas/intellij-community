package org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.surroundersImpl.blocks.open;

import org.jetbrains.plugins.groovy.lang.surroundWith.surrounders.GroovyManyStatementsSurrounder;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrWithStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 25.05.2007
 */
public class GroovyWithWithStatementsSurrounder extends GroovyManyStatementsSurrounder {
  protected String getElementsTemplateAsString(ASTNode... nodes) {
    return "with (a) {\n " + super.getListElementsTemplateAsString(nodes) + "\n }";
  }

  protected TextRange getSurroundSelectionRange(GroovyPsiElement element) {
    assert element instanceof GrWithStatement;

    GrWithStatement grWithStatement = (GrWithStatement) element;
    GrCondition condition = grWithStatement.getCondition();
    int endOffset = condition.getTextRange().getEndOffset();

    condition.getParent().getNode().removeChild(condition.getNode());

    return new TextRange(endOffset, endOffset);
  }

  protected boolean isApplicable(PsiElement element) {
    return element instanceof GrStatement && ! (element instanceof GrExpression);
  }

  public String getTemplateDescription() {
    return "with ()";
  }
}
