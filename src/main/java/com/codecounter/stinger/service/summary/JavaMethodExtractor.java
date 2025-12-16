package com.codecounter.stinger.service.summary;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithRange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class JavaMethodExtractor {

    private JavaMethodExtractor() {
    }

    public record ExtractedMethod(String stableId, String displayName, String code) {
    }

    public static List<ExtractedMethod> extractMethods(Path javaFile) throws IOException {
        String src = Files.readString(javaFile, StandardCharsets.UTF_8);

        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(src);
        } catch (ParseProblemException e) {
            return List.of();
        }

        List<ExtractedMethod> methods = new ArrayList<>();

        for (ClassOrInterfaceDeclaration cls : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            String className = cls.getNameAsString();

            for (MethodDeclaration m : cls.getMethods()) {
                methods.add(toExtracted("method", className, m, src));
            }
            for (ConstructorDeclaration c : cls.getConstructors()) {
                methods.add(toExtracted("ctor", className, c, src));
            }
        }

        return methods;
    }

    private static ExtractedMethod toExtracted(String kind, String className, CallableDeclaration<?> decl, String fullSource) {
        String signature = decl.getDeclarationAsString(false, false, false);
        String stableId = kind + ":" + className + "." + decl.getNameAsString() + "(" + decl.getParameters().size() + ")";

        String code = extractRange(fullSource, decl);
        String display = className + "." + signature;
        return new ExtractedMethod(stableId, display, code);
    }

    private static String extractRange(String fullSource, NodeWithRange<?> node) {
        if (node.getRange().isEmpty()) {
            return "";
        }
        int beginLine = node.getRange().get().begin.line;
        int endLine = node.getRange().get().end.line;

        String[] lines = fullSource.split("\\R", -1);
        int startIdx = Math.max(1, beginLine);
        int endIdx = Math.min(lines.length, endLine);

        StringBuilder sb = new StringBuilder();
        for (int i = startIdx; i <= endIdx; i++) {
            sb.append(lines[i - 1]).append('\n');
        }
        return sb.toString();
    }
}
