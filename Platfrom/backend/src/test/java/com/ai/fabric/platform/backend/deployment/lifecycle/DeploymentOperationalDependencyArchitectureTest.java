package com.ai.fabric.platform.backend.deployment.lifecycle;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentOperationalDependencyArchitectureTest {
    private static final List<String> RETIRED_PACKAGES = List.of(
        "com.ai.fabric.platform.backend.deployment.admission",
        "com.ai.fabric.platform.backend.deployment.retirement",
        "com.ai.fabric.platform.backend.deployment.generation");

    @Test void mainSourcesCannotDeclareOrDependOnRetiredOperationalPackages() throws Exception {
        Path sourceRoot = Path.of("src/main/java");
        assertThat(sourceRoot).isDirectory();
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler for syntax-only architecture checks").isNotNull();
        var violations = new ArrayList<String>();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var paths = Files.walk(sourceRoot); var files = compiler.getStandardFileManager(diagnostics, null, null)) {
            var sources = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
            var task = (JavacTask) compiler.getTask(null, files, diagnostics, List.of("-proc:none"), null,
                files.getJavaFileObjectsFromPaths(sources));
            for (CompilationUnitTree unit : task.parse()) {
                String file = unit.getSourceFile().getName();
                if (unit.getPackageName() != null) reject(unit.getPackageName().toString(), file, violations);
                unit.getImports().forEach(value -> reject(value.getQualifiedIdentifier().toString(), file, violations));
                new TreeScanner<Void, Void>() {
                    @Override public Void visitMemberSelect(MemberSelectTree node, Void unused) {
                        reject(node.toString(), file, violations);
                        return super.visitMemberSelect(node, unused);
                    }
                }.scan(unit, null);
            }
        }
        assertThat(diagnostics.getDiagnostics()).filteredOn(value -> value.getKind() == javax.tools.Diagnostic.Kind.ERROR)
            .as("main Java syntax").isEmpty();
        assertThat(violations).as("retired operational dependencies in main artifact sources").isEmpty();
    }

    @Test void ordinaryAiGenerationAndHistoricalDataStringsAreNotForbiddenDependencies() {
        var violations = new ArrayList<String>();
        reject("com.ai.fabric.platform.backend.ai.generation", "ai-generation", violations);
        reject("com.ai.fabric.platform.backend.deployment.generationPolicy", "unrelated-name", violations);
        assertThat(violations).isEmpty();
        reject(RETIRED_PACKAGES.get(2) + ".DeploymentRuntimeGenerationService", "retired-engine", violations);
        assertThat(violations).hasSize(1);
    }

    private static void reject(String name, String source, List<String> violations) {
        if (RETIRED_PACKAGES.stream().anyMatch(prefix -> name.equals(prefix) || name.startsWith(prefix + ".")))
            violations.add(source + ": " + name);
    }
}
