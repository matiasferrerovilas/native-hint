package dev.nativehint.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

/**
 * Se aplica con {@code plugins { id 'dev.nativehint' } } en cualquier proyecto Java/Gradle.
 * Espera a que se aplique el plugin 'java' (en cualquier orden) y registra la tarea
 * {@code hintHunterScan} apuntando al sourceSet 'main' de ese proyecto — no asume nada propio.
 */
public class HintHunterPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
			JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
			SourceSet main = javaExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

			project.getTasks().register("hintHunterScan", HintHunterScanTask.class, task -> {
				task.setGroup("verification");
				task.setDescription("Busca tipos que necesitan runtime hints explícitos para GraalVM native-image.");
				task.getSourceDirectories().from(main.getAllJava().getSrcDirs());
			});
		});
	}
}
