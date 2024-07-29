package com.mikan.intellij.plugin.inspection;

import java.util.List;
import java.util.Objects;

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
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

    private static final String AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired";
    private static final String RESOURCE = "javax.annotation.Resource";

    private static final List<String> ANNOTATIONS = List.of(AUTOWIRED, RESOURCE);

    private final FieldInjectionQuickFix fieldInjectionQuickFix = new FieldInjectionQuickFix();

    @Override
    public ProblemDescriptor @Nullable [] checkField(@NotNull PsiField field, @NotNull InspectionManager manager,
        boolean isOnTheFly) {
        boolean inTestSourceContent = ProjectRootManager.getInstance(manager.getProject())
            .getFileIndex()
            .isInTestSourceContent(field.getContainingFile().getVirtualFile());
        if (inTestSourceContent) {
            return null;
        }

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
            if (containingClass == null) {
                return;
            }

            PsiMethod constructor = this.buildConstructor(project, containingClass);
            containingClass.add(constructor);
        }

        private @NotNull PsiMethod buildConstructor(@NotNull Project project, PsiClass containingClass) {
            String className = containingClass.getName();
            assert className != null;

            PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
            PsiMethod constructor = factory.createConstructor(className);
            this.addAutowiredAnnotation(containingClass, constructor, factory);

            PsiParameterList parameterList = constructor.getParameterList();
            PsiCodeBlock constructorBody = constructor.getBody();
            assert constructorBody != null;

            PsiField[] fields = containingClass.getFields();
            for (PsiField field : fields) {
                if (this.isStaticField(field)) {
                    continue;
                }
                // 字段删除 @Autowired 或 @Resource 注解
                this.deleteFiledInjectionAnnotation(field);
                // 字段添加 private 和 final 修饰符
                this.addPrivateModifier(field, factory);
                this.addFinalModifier(field, factory);

                String fieldName = field.getName();
                // 添加构造方法参数
                parameterList.add(factory.createParameter(fieldName, field.getType()));
                // 添加构造方法参数赋值
                constructorBody.add(factory.createStatementFromText(
                    "this." + fieldName + " = " + fieldName + ";", null));
            }

            return constructor;
        }

        private void addAutowiredAnnotation(PsiClass containingClass, PsiMethod constructor,
            PsiElementFactory factory) {
            this.addAutowiredAnnotation(constructor, factory);
            this.addAutowiredImport(containingClass, factory);
        }

        private void addAutowiredAnnotation(PsiMethod constructor, PsiElementFactory factory) {
            PsiElement annotation = factory.createAnnotationFromText("@Autowired", null);
            PsiModifierList modifierList = constructor.getModifierList();
            PsiElement firstChild = modifierList.getFirstChild();
            modifierList.addBefore(annotation, firstChild);
        }

        private void addAutowiredImport(PsiClass containingClass, PsiElementFactory factory) {
            PsiFile psiFile = containingClass.getContainingFile();
            if (!(psiFile instanceof PsiJavaFile containingFile)) {
                return;
            }

            PsiImportList importList = containingFile.getImportList();
            if (importList == null) {
                return;
            }

            if (this.hasAutowiredImport(importList)) {
                return;
            }

            PsiImportStatement psiImportStatement = factory.createImportStatementOnDemand(AUTOWIRED);
            importList.add(psiImportStatement);

            JavaCodeStyleManager.getInstance(containingClass.getProject())
                .optimizeImports(containingFile);
        }

        private boolean hasAutowiredImport(PsiImportList importList) {
            for (PsiImportStatement importStatement : importList.getImportStatements()) {
                if (AUTOWIRED.equals(importStatement.getQualifiedName())) {
                    return true;
                }
            }
            return false;
        }

        private boolean isStaticField(PsiField field) {
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList == null) {
                return false;
            }
            return modifierList.hasExplicitModifier(PsiModifier.STATIC);
        }

        private void addPrivateModifier(PsiField field, PsiElementFactory factory) {
            if (!this.hasVisibleModifier(field)) {
                Objects.requireNonNull(field.getModifierList()).add(factory.createKeyword(PsiKeyword.PRIVATE));
            }
        }

        private boolean hasVisibleModifier(PsiField field) {
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList == null) {
                return true;
            }

            return modifierList.hasExplicitModifier(PsiModifier.PRIVATE)
                || modifierList.hasExplicitModifier(PsiModifier.PROTECTED)
                || modifierList.hasExplicitModifier(PsiModifier.PUBLIC);
        }

        private void addFinalModifier(PsiField field, PsiElementFactory factory) {
            if (!this.hasFinalModifier(field)) {
                Objects.requireNonNull(field.getModifierList()).add(factory.createKeyword(PsiKeyword.FINAL));
            }
        }

        private boolean hasFinalModifier(PsiField field) {
            PsiModifierList modifierList = field.getModifierList();
            if (modifierList == null) {
                return true;
            }

            return modifierList.hasExplicitModifier(PsiModifier.FINAL);
        }

        private void deleteFiledInjectionAnnotation(PsiField field) {
            PsiAnnotation[] annotations = field.getAnnotations();
            for (PsiAnnotation annotation : annotations) {
                if (ANNOTATIONS.contains(annotation.getQualifiedName())) {
                    annotation.delete();
                }
            }
        }

    }

}
