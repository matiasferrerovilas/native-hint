package dev.nativehint.gradle;

import dev.nativehint.HintCandidate;
import dev.nativehint.HintScanner;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.List;

/**
 * Corre {@link HintScanner} sobre las carpetas de fuentes del proyecto que aplica el plugin. No
 * asume ningún paquete ni estructura en particular: recibe los directorios ya resueltos por
 * {@link HintHunterPlugin} desde el sourceSet de Gradle.
 */
public abstract class HintHunterScanTask extends DefaultTask {

	@InputFiles
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract ConfigurableFileCollection getSourceDirectories();

	@TaskAction
	public void scan() {
		HintScanner scanner = new HintScanner();
		boolean anyFound = false;
		for (File dir : getSourceDirectories().getFiles()) {
			if (!dir.exists()) {
				continue;
			}
			List<HintCandidate> found = scanner.scanDirectory(dir.toPath());
			if (found.isEmpty()) {
				continue;
			}
			anyFound = true;
			getLogger().lifecycle("native-hint: {} candidato(s) a runtime hints en {}", found.size(), dir);
			for (HintCandidate c : found) {
				getLogger().lifecycle("  [{}] {}:{} -> {}", c.reason(), c.file(), c.line(), c.typeName());
			}
		}
		if (!anyFound) {
			getLogger().lifecycle("native-hint: no se encontraron candidatos a runtime hints.");
		}
	}
}
