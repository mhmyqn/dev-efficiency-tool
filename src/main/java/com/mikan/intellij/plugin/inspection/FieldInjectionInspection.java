package com.mikan.intellij.plugin.inspection;

import java.util.List;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Field injection inspection
 *
 * @author mikan
 * @date 2024-07-28 16:03
 */
public class FieldInjectionInspection extends AbstractBaseJavaLocalInspectionTool {

    private static final List<String> ANNOTATIONS = List.of(
        "org.springframework.beans.factory.annotation.Autowired",
        "javax.annotation.Resource"
    );

    private final FieldInjectionQuickFix fieldInjectionQuickFix = new FieldInjectionQuickFix();

    @Override
    public ProblemDescriptor @Nullable [] checkField(@NotNull PsiField field, @NotNull InspectionManager manager,
        boolean isOnTheFly) {
        PsiAnnotation[] annotations = field.getAnnotations();
        for (PsiAnnotation annotation : annotations) {
            if (ANNOTATIONS.contains(annotation.getQualifiedName())) {
                ProblemDescriptor problemDescriptor = manager.createProblemDescriptor(field,
                    InspectionBundle.message("inspection.field.injection.problem.descriptor"),
                    fieldInjectionQuickFix,
                    ProblemHighlightType.WARNING,
                    isOnTheFly);
                return new ProblemDescriptor[] {problemDescriptor};
            }
        }

        return null;
    }

    private static class FieldInjectionQuickFix implements LocalQuickFix {

        @Override
        public @IntentionName @NotNull String getName() {
            return InspectionBundle.message("inspection.field.injection.use.quickfix");
        }

        @Override
        public @IntentionFamilyName @NotNull String getFamilyName() {
            return this.getName();
        }

        @Override
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiField psiField = (PsiField)descriptor.getPsiElement();
            PsiClass containingClass = PsiTreeUtil.getParentOfType(psiField, PsiClass.class);
            PsiField[] fields = containingClass.getFields();

            String className = containingClass.getName();
            assert className != null;

            PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            PsiMethod constructor = factory.createConstructor(className);
            // TODO 2024-07-28 16:50 Mikan
        }
    }

}
