package com.siyeh.ig.threading;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ThreadWithDefaultRunMethodInspection extends ExpressionInspection{
    public String getID(){
        return "InstantiatingAThreadWithDefaultRunMethod";
    }
    public String getDisplayName(){
        return "Instantiating a Thread with default 'run()' method";
    }

    public String getGroupDisplayName(){
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Instantiating a #ref with default 'run()' method #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ThreadWithDefaultRunMethodVisitor();
    }

    private static class ThreadWithDefaultRunMethodVisitor
            extends BaseInspectionVisitor{

        public void visitNewExpression(@NotNull PsiNewExpression expression){
            super.visitNewExpression(expression);
            final PsiAnonymousClass anonymousClass =
                    expression.getAnonymousClass();

            if(anonymousClass != null){
                final PsiJavaCodeReferenceElement baseClassReference =
                        anonymousClass.getBaseClassReference();
                final PsiElement referent = baseClassReference.resolve();
                if(referent == null){
                    return;
                }
                final PsiClass referencedClass = (PsiClass) referent;
                final String referencedClassName =
                        referencedClass.getQualifiedName();
                if(!"java.lang.Thread".equals(referencedClassName)){
                    return;
                }
                if(definesRun(anonymousClass)){
                    return;
                }
                final PsiExpressionList argumentList =
                        expression.getArgumentList();
                if(argumentList == null){
                    return;
                }
                final PsiExpression[] args = argumentList.getExpressions();
                if(args == null){
                    return;
                }
                for(PsiExpression arg : args){
                    if(TypeUtils.expressionHasTypeOrSubtype("java.lang.Runnable",
                                                            arg)){
                        return;
                    }
                }
                registerError(baseClassReference);
            } else{
                final PsiJavaCodeReferenceElement classReference =
                        expression.getClassReference();
                if(classReference == null){
                    return;
                }
                final PsiElement referent = classReference.resolve();
                if(referent == null){
                    return;
                }
                final PsiClass referencedClass = (PsiClass) referent;
                final String referencedClassName =
                        referencedClass.getQualifiedName();
                if(!"java.lang.Thread".equals(referencedClassName)){
                    return;
                }
                final PsiExpressionList argumentList =
                        expression.getArgumentList();
                if(argumentList == null){
                    return;
                }
                final PsiExpression[] args = argumentList.getExpressions();
                if(args == null){
                    return;
                }
                for(PsiExpression arg : args){
                    if(TypeUtils.expressionHasTypeOrSubtype("java.lang.Runnable",
                                                            arg)){
                        return;
                    }
                }
                registerError(classReference);
            }

        }

        private static boolean definesRun(PsiAnonymousClass aClass){
            final PsiMethod[] methods = aClass.getMethods();
            for(final PsiMethod method : methods){
                final String methodName = method.getName();
                if("run".equals(methodName)){
                    final PsiParameterList parameterList =
                            method.getParameterList();
                    if(parameterList != null){
                        final PsiParameter[] parameters =
                                parameterList.getParameters();
                        if(parameters != null && parameters.length == 0){
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
