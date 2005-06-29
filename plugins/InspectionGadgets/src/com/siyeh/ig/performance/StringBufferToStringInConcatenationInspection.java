package com.siyeh.ig.performance;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class StringBufferToStringInConcatenationInspection extends ExpressionInspection {
    private final StringBufferToStringFix fix = new StringBufferToStringFix();

    public String getDisplayName() {
        return "StringBuffer.toString() in concatenation";
    }

    public String getGroupDisplayName() {
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Calls to StringBuffer.#ref() in concatenation #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new StringBufferToStringVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class StringBufferToStringFix extends InspectionGadgetsFix {
        public String getName() {
            return "Remove .toString()";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement methodNameToken = descriptor.getPsiElement();
            final PsiElement methodCallExpression = methodNameToken.getParent();
            assert methodCallExpression != null;
            final PsiMethodCallExpression methodCall =
                    (PsiMethodCallExpression) methodCallExpression.getParent();
            assert methodCall != null;
            final PsiReferenceExpression expression = methodCall.getMethodExpression();
            final PsiExpression qualifier = expression.getQualifierExpression();
            final String newExpression = qualifier.getText();
            replaceExpression(methodCall, newExpression);
        }
    }

    private static class StringBufferToStringVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiBinaryExpression)) {
                return;
            }
            final PsiBinaryExpression parentBinary = (PsiBinaryExpression) parent;
            final PsiJavaToken sign = parentBinary.getOperationSign();
            if (!sign.getTokenType().equals(JavaTokenType.PLUS)) {
                return;
            }
            final PsiExpression rhs = parentBinary.getROperand();
            if (rhs == null) {
                return;
            }
            if (!rhs.equals(expression)) {
                return;
            }
            if (!isStringBufferToString(expression)) {
                return;
            }
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if (methodExpression == null) {
                return;
            }
            registerMethodCallError(expression);
        }

        private static boolean isStringBufferToString(PsiMethodCallExpression expression) {
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return false;
            }
            final String methodName = method.getName();
            if (methodName == null) {
                return false;
            }
            if (!"toString".equals(methodName)) {
                return false;
            }
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList == null) {
                return false;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            if (parameters.length != 0) {
                return false;
            }
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null)
            {
                return false;
            }
            final String className = aClass.getQualifiedName();
            return "java.lang.StringBuffer".equals(className);
        }
    }

}
