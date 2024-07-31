package com.mikan.intellij.plugin.intention;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPackageStatement;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.createTest.CreateTestUtils;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author mikan
 * @date 2024-07-28 11:50
 */
public class CreateJunit5TestWithMockitoIntentionAction extends PsiElementBaseIntentionAction {

    @Override
    public @NotNull @IntentionName String getText() {
        return "Create junit5 test with mockito.";
    }

    @Override
    public @NotNull @IntentionFamilyName String getFamilyName() {
        return this.getText();
    }

    @Override
    public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
        PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        return containingClass != null;
    }

    @Override
    public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element)
        throws IncorrectOperationException {
        PsiClass containingClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (containingClass == null) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> createTestJavaFile(containingClass));
    }

    private void createTestJavaFile(PsiClass srcClass) {
        Project project = srcClass.getProject();
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);

        PsiJavaFile srcJavaFile = (PsiJavaFile)srcClass.getContainingFile();
        final Module srcModule = ModuleUtilCore.findModuleForPsiElement(srcClass);
        assert srcModule != null;

        PsiDirectory targetDirectory = CreateTestUtils.selectTargetDirectory(srcJavaFile.getPackageName(),
            project, srcModule);
        assert targetDirectory != null;

        // 1. 创建文件
        String className = srcClass.getName() + "Test";
        PsiFile targetFile = targetDirectory.createFile(className + ".java");
        PsiJavaFile targetJavaFile = (PsiJavaFile)targetFile;

        // 2. 生成 package
        this.generatePackageStatement(targetJavaFile, srcJavaFile);

        List<String> imports = Lists.newArrayList();

        // 3. 生成类的内容
        PsiClass targetClass = this.generateClass(elementFactory, className, srcClass, project, imports);
        assert targetClass != null;
        targetJavaFile.add(targetClass);

        // 4. 添加 import
        this.generateImportList(imports, targetJavaFile, project);

        // 5. 格式化
        JavaCodeStyleManager.getInstance(project).optimizeImports(targetJavaFile);
        CodeStyleManager.getInstance(project).reformat(targetJavaFile);

        // 6. 打开测试文件，并将光标定位到左括号 { 的位置，但这里有问题，定位不到 { 的位置
        CodeInsightUtil.positionCursorAtLBrace(project, targetJavaFile, targetClass);
    }

    private void generatePackageStatement(PsiJavaFile targetJavaFile, PsiJavaFile srcJavaFile) {
        PsiPackageStatement packageStatement = srcJavaFile.getPackageStatement();
        assert packageStatement != null;
        targetJavaFile.addBefore(packageStatement.copy(), targetJavaFile.getFirstChild());
    }

    private void generateImportList(List<String> imports, PsiJavaFile targetJavaFile, Project project) {
        for (String anImport : imports) {
            PsiImportStatement dynamicImport = this.generateDynamicImport(project, anImport);
            this.addImport(targetJavaFile, dynamicImport);
        }
    }

    private PsiClass generateClass(PsiElementFactory elementFactory, String className,
        PsiClass srcClass, Project project, List<String> imports) {
        PsiDirectory targetDirectory = srcClass.getContainingFile().getContainingDirectory();
        final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(targetDirectory);
        if (aPackage != null) {
            final GlobalSearchScope scope = GlobalSearchScopesCore.directoryScope(targetDirectory, false);
            final PsiClass[] classes = aPackage.findClassByShortName(className, scope);
            if (classes.length > 0) {
                if (!FileModificationService.getInstance().preparePsiElementForWrite(classes[0])) {
                    return null;
                }
                return classes[0];
            }
        }
        // 创建类
        PsiClass targetClass = elementFactory.createClass(className);
        // --PsiClass psiClass = JavaDirectoryService.getInstance().createClass(directory, className);--

        // 1. 生成类注解
        this.generateClassAnnotation(elementFactory, targetClass, imports);

        // 2. 生成 mock 字段
        List<PsiField> generatedFields = this.generateMockFields(elementFactory, srcClass, targetClass, imports);

        // 3. 生成测试目标字段
        PsiField generateTargetField = this.generateTargetField(elementFactory, srcClass, targetClass);

        // 4. 生成 setUp 方法
        this.generateSetUpMethod(elementFactory, targetClass, srcClass, generatedFields,
            generateTargetField, imports);

        // 5. 生成测试方法
        this.generateTestMethod(elementFactory, srcClass, targetClass, imports);

        // 添加空行
        this.generateEmptyLineAfter(targetClass, project, targetClass.getLBrace());
        this.generateEmptyLineBefore(targetClass, project, targetClass.getRBrace());

        return targetClass;
    }

    private void generateClassAnnotation(PsiElementFactory elementFactory, PsiClass targetClass, List<String> imports) {
        PsiAnnotation extendWithAnnotation = elementFactory.createAnnotationFromText(
            "@ExtendWith(MockitoExtension.class)", targetClass);
        PsiModifierList modifierList = targetClass.getModifierList();
        assert modifierList != null;
        modifierList.addBefore(extendWithAnnotation, modifierList.getFirstChild());
        // 删除 public 修饰符
        modifierList.setModifierProperty(PsiModifier.PUBLIC, false);

        imports.add("org.junit.jupiter.api.extension.ExtendWith");
        imports.add("org.mockito.junit.jupiter.MockitoExtension");
    }

    private void generateEmptyLineBefore(PsiClass targetClass, Project project, PsiElement anchor) {
        PsiParserFacade psiParserFacade = PsiParserFacade.getInstance(project);
        PsiElement whiteSpace = psiParserFacade.createWhiteSpaceFromText("\n\n");
        targetClass.addBefore(whiteSpace, anchor);
    }

    private void generateEmptyLineAfter(PsiClass targetClass, Project project, PsiElement anchor) {
        PsiParserFacade psiParserFacade = PsiParserFacade.getInstance(project);
        PsiElement whiteSpace = psiParserFacade.createWhiteSpaceFromText("\n\n");
        targetClass.addAfter(whiteSpace, anchor);
    }

    private void generateSetUpMethod(PsiElementFactory elementFactory, PsiClass targetClass,
        PsiClass srcClass, List<PsiField> generatedFields, PsiField targetField, List<String> imports) {
        PsiMethod setUp = elementFactory.createMethod("setUp", PsiTypes.voidType());

        PsiModifierList modifierList = setUp.getModifierList();
        modifierList.setModifierProperty(PsiModifier.PUBLIC, false);

        PsiAnnotation beforeEachAnnotation = elementFactory.createAnnotationFromText("@BeforeEach", setUp);
        modifierList.addBefore(beforeEachAnnotation, modifierList.getFirstChild());

        PsiCodeBlock methodBody = setUp.getBody();
        assert methodBody != null;

        String fieldNames = generatedFields.stream()
            .map(PsiField::getName)
            .collect(Collectors.joining(", "));
        PsiStatement statement = elementFactory.createStatementFromText("this." + targetField.getName()
            + " = new " + srcClass.getName() + "(" + fieldNames + ");", setUp);
        methodBody.add(statement);

        targetClass.add(setUp);

        imports.add("org.junit.jupiter.api.BeforeEach");
    }

    private void generateTestMethod(PsiElementFactory elementFactory, PsiClass srcClass,
        PsiClass targetClass, List<String> imports) {
        PsiMethod[] methods = srcClass.getMethods();
        List<PsiMethod> testMethods = Arrays.stream(methods)
            .filter(method -> !method.hasModifierProperty(PsiModifier.STATIC)
                && !method.hasModifierProperty(PsiModifier.PRIVATE)
                && !method.isConstructor())
            .map(method -> this.generateTestMethod(elementFactory, method))
            .peek(targetClass::add)
            .toList();

        if (!testMethods.isEmpty()) {
            imports.add("org.junit.jupiter.api.Test");
        }
    }

    private PsiMethod generateTestMethod(PsiElementFactory elementFactory, PsiMethod method) {
        String methodName = method.getName();
        String testMethodName = "should_" + methodName + "_successfully";
        PsiMethod testMethod = elementFactory.createMethod(testMethodName, PsiTypes.voidType());

        PsiModifierList modifierList = testMethod.getModifierList();
        modifierList.setModifierProperty(PsiModifier.PUBLIC, false);

        PsiAnnotation testAnnotation = elementFactory.createAnnotationFromText("@Test", testMethod);
        modifierList.addBefore(testAnnotation, modifierList.getFirstChild());

        //PsiCodeBlock methodBody = elementFactory.createCodeBlockFromText("{\n System.out.println(\"This is a test
        // method\");\n}", method);
        //method.getBody().replace(methodBody);

        return testMethod;
    }

    private List<PsiField> generateMockFields(PsiElementFactory elementFactory, PsiClass srcClass,
        PsiClass targetClass, List<String> imports) {
        PsiField[] fields = srcClass.getFields();
        List<PsiField> needMockFields = Arrays.stream(fields)
            .filter(field -> !field.hasModifierProperty(PsiModifier.STATIC))
            .toList();
        List<PsiField> generatedFields = needMockFields.stream()
            .map(field -> {
                PsiAnnotation mockAnnotation = elementFactory.createAnnotationFromText("@Mock", field);
                PsiField psiField = elementFactory.createField(field.getName(), field.getType());
                PsiModifierList fieldModifierList = psiField.getModifierList();
                assert fieldModifierList != null;
                fieldModifierList.addBefore(mockAnnotation, fieldModifierList.getFirstChild());
                targetClass.add(psiField);

                imports.add(field.getType().getCanonicalText());

                return psiField;
            })
            .toList();

        if (!generatedFields.isEmpty()) {
            imports.add("org.mockito.Mock");
        }

        return generatedFields;
    }

    private PsiField generateTargetField(PsiElementFactory elementFactory, PsiClass srcClass, PsiClass targetClass) {
        String className = srcClass.getName();
        String fieldName = StringUtils.uncapitalize(className);
        PsiField psiField = elementFactory.createFieldFromText("private " + className + " " + fieldName + ";",
            targetClass);
        targetClass.add(psiField);

        return psiField;
    }

    private PsiImportStatement generateDynamicImport(Project project, String qualifiedName) {
        PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
        PsiClass importClass = JavaPsiFacade.getInstance(project)
            .findClass(qualifiedName, GlobalSearchScope.allScope(project));
        if (importClass == null) {
            return null;
        }
        return elementFactory.createImportStatement(importClass);
    }

    private void addImport(PsiJavaFile targetJavaFile, PsiImportStatement extendWithImport) {
        if (extendWithImport == null) {
            return;
        }

        PsiImportList importList = targetJavaFile.getImportList();
        assert importList != null;
        importList.add(extendWithImport);
    }

}
