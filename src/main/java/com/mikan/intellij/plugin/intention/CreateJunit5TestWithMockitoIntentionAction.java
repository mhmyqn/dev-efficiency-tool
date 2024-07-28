package com.mikan.intellij.plugin.intention;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author mikan
 * @date 2024-07-28 11:50
 */
public class CreateJunit5TestWithMockitoIntentionAction extends PsiElementBaseIntentionAction {

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return "Create junit5 test with mockito.";
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        if (element == null) {
            return false;
        }

        PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (containingClass == null) {
            return false;
        }

        return true;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element)
        throws IncorrectOperationException {

    }

}
