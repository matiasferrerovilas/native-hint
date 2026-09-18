package dev.nativehint.gradle;

import dev.nativehint.HintCandidate;
import dev.nativehint.HintScanner;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Genera un {@code RuntimeHintsRegistrar} real (código Java compilable) con los tipos detectados
 * por {@link HintScanner}, más el {@code META-INF/spring/aot.factories} que hace que Spring lo
 * descubra y lo invoque solo — sin {@code @ImportRuntimeHints} manual y sin mantener una lista a
 * mano. El resultado se agrega como fuente/recurso generado del sourceSet, así que corre en
 * cualquier build normal (compileJava, bootRun, la compilación native de GraalVM), no hace falta
 * invocar esta tarea a mano.
 */
public abstract class HintHunterGenerateTask extends DefaultTask {

	private static final String GENERATED_PACKAGE = "nativehint.generated";
	private static final String GENERATED_CLASS = "NativeHintGeneratedRegistrar";

	@InputFiles
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract ConfigurableFileCollection getSourceDirectories();

	@OutputDirectory
	public abstract DirectoryProperty getGeneratedSourcesDir();

	@OutputDirectory
	public abstract DirectoryProperty getGeneratedResourcesDir();

	@TaskAction
	public void generate() {
		HintScanner scanner = new HintScanner();
		List<File> existingDirs = new ArrayList<>();
		for (File dir : getSourceDirectories().getFiles()) {
			if (dir.exists()) {
				existingDirs.add(dir);
			}
		}

		boolean enabled = existingDirs.stream().anyMatch(dir -> scanner.hasNativeHintMarker(dir.toPath()));
		if (!enabled) {
			ensureOutputDirsExist();
			getLogger().lifecycle("native-hint: no encontré ninguna clase anotada con @dev.nativehint.NativeHint "
					+ "- no genero nada. Anotá tu clase @SpringBootApplication con @NativeHint para activarlo.");
			return;
		}

		List<HintCandidate> all = new ArrayList<>();
		for (File dir : existingDirs) {
			all.addAll(scanner.scanDirectory(dir.toPath()));
		}

		Set<String> directFqns = new LinkedHashSet<>();
		for (HintCandidate c : all) {
			if (isFullyQualified(c.typeName())) {
				directFqns.add(c.typeName());
			} else {
				getLogger().warn("native-hint: no pude resolver el tipo completo de '{}' en {}:{} "
								+ "(dependencia fuera del classpath conocido, tipo genérico, etc.) "
								+ "- registralo a mano si hace falta.",
						c.typeName(), c.file(), c.line());
			}
		}

		List<Path> sourceRoots = existingDirs.stream().map(File::toPath).collect(java.util.stream.Collectors.toList());
		Set<String> resolvedFqns = scanner.expandNestedFieldTypes(directFqns, sourceRoots);

		try {
			writeRegistrarSource(resolvedFqns);
			writeAotFactories();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		getLogger().lifecycle("native-hint: {} tipo(s) registrados automáticamente para GraalVM native-image "
						+ "({} detectados directamente + {} campos anidados expandidos).",
				resolvedFqns.size(), directFqns.size(), resolvedFqns.size() - directFqns.size());
	}

	private void ensureOutputDirsExist() {
		try {
			Files.createDirectories(getGeneratedSourcesDir().get().getAsFile().toPath());
			Files.createDirectories(getGeneratedResourcesDir().get().getAsFile().toPath());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private boolean isFullyQualified(String typeName) {
		return typeName.contains(".") && !typeName.startsWith("<unresolved");
	}

	private void writeRegistrarSource(Set<String> fqns) throws IOException {
		Path packageDir = getGeneratedSourcesDir().get().getAsFile().toPath()
				.resolve(GENERATED_PACKAGE.replace('.', '/'));
		Files.createDirectories(packageDir);

		StringBuilder src = new StringBuilder();
		src.append("package ").append(GENERATED_PACKAGE).append(";\n\n");
		src.append("import org.springframework.aot.hint.MemberCategory;\n");
		src.append("import org.springframework.aot.hint.RuntimeHints;\n");
		src.append("import org.springframework.aot.hint.RuntimeHintsRegistrar;\n\n");
		src.append("public class ").append(GENERATED_CLASS).append(" implements RuntimeHintsRegistrar {\n\n");
		src.append("\tprivate static final Class<?>[] TYPES = {\n");
		for (String fqn : fqns) {
			src.append("\t\t\t").append(fqn).append(".class,\n");
		}
		src.append("\t};\n\n");
		src.append("\t@Override\n");
		src.append("\tpublic void registerHints(RuntimeHints hints, ClassLoader classLoader) {\n");
		src.append("\t\tfor (Class<?> type : TYPES) {\n");
		src.append("\t\t\thints.reflection().registerType(type, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,\n");
		src.append("\t\t\t\t\tMemberCategory.INVOKE_DECLARED_METHODS);\n");
		src.append("\t\t}\n");
		src.append("\t}\n");
		src.append("}\n");

		Files.writeString(packageDir.resolve(GENERATED_CLASS + ".java"), src.toString());
	}

	private void writeAotFactories() throws IOException {
		Path springDir = getGeneratedResourcesDir().get().getAsFile().toPath().resolve("META-INF/spring");
		Files.createDirectories(springDir);
		Files.writeString(springDir.resolve("aot.factories"),
				"org.springframework.aot.hint.RuntimeHintsRegistrar=" + GENERATED_PACKAGE + "." + GENERATED_CLASS + "\n");
	}
}
