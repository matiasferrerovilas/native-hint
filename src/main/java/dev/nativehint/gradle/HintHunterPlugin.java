package dev.nativehint.gradle;

import org.gradle.api.Project;
import org.gradle.api.Plugin;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.util.stream.Collectors;

/**
 * Se aplica con {@code plugins { id 'io.github.matiasferrerovilas.nativehint' } } en cualquier
 * proyecto Java/Gradle. Espera a que se aplique el plugin 'java' (en cualquier orden) y:
 * <ul>
 *   <li>{@code hintHunterScan}: solo reporta candidatos por stdout, no toca el build.</li>
 *   <li>{@code hintHunterGenerate}: genera el {@code RuntimeHintsRegistrar} real y lo agrega como
 *       fuente/recurso generado del sourceSet 'main', así corre automáticamente en cualquier build
 *       normal sin que haya que invocarlo a mano ni mantener nada escrito.</li>
 * </ul>
 * No asume ningún paquete ni estructura propia del consumidor.
 */
public class HintHunterPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
			JavaPluginExtension javaExtension = project.getExtensions().getByType(JavaPluginExtension.class);
			SourceSet main = javaExtension.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);

			Provider<Directory> generatedSourcesDir = project.getLayout().getBuildDirectory()
					.dir("generated/sources/nativeHint/java/main");
			Provider<Directory> generatedResourcesDir = project.getLayout().getBuildDirectory()
					.dir("generated/sources/nativeHint/resources/main");

			// Excluye la propia carpeta generada del escaneo: main.getJava() la va a incluir una
			// vez que se agrega más abajo, y sin este filtro cada build re-escanearía su propia
			// salida del build anterior.
			org.gradle.api.provider.Provider<Iterable<File>> ownSourceDirs = project.provider(() ->
					main.getJava().getSrcDirs().stream()
							.filter(dir -> !dir.equals(generatedSourcesDir.get().getAsFile()))
							.collect(Collectors.toList()));

			project.getTasks().register("hintHunterScan", HintHunterScanTask.class, task -> {
				task.setGroup("verification");
				task.setDescription("Busca tipos que necesitan runtime hints explícitos para GraalVM native-image (solo reporta, no genera nada).");
				task.getSourceDirectories().from(ownSourceDirs);
			});

			TaskProvider<HintHunterGenerateTask> generateTask = project.getTasks()
					.register("hintHunterGenerate", HintHunterGenerateTask.class, task -> {
						task.setGroup("build");
						task.setDescription("Genera y registra automáticamente los runtime hints de GraalVM native-image detectados en el código fuente.");
						task.getSourceDirectories().from(ownSourceDirs);
						task.getGeneratedSourcesDir().set(generatedSourcesDir);
						task.getGeneratedResourcesDir().set(generatedResourcesDir);
					});

			// afterEvaluate: muchos proyectos (p.ej. los generados por Spring Initializr) tienen
			// su propio bloque `sourceSets { main { java { srcDirs = [...] } } }` más abajo en el
			// build.gradle, con '=' en vez de '+='. Esa asignación reemplaza TODO el set de
			// srcDirs, así que si agregamos la carpeta generada acá arriba (durante apply(), que
			// corre antes de que el resto del build.gradle del consumidor se evalúe), esa
			// asignación posterior la pisa sin dejar rastro. Agregarla en afterEvaluate garantiza
			// que corre después de que el script del consumidor ya terminó de ejecutarse.
			project.afterEvaluate(p -> {
				main.getJava().srcDir(generatedSourcesDir);
				main.getResources().srcDir(generatedResourcesDir);
			});

			project.getTasks().named(main.getCompileJavaTaskName()).configure(t -> t.dependsOn(generateTask));
			project.getTasks().named(main.getProcessResourcesTaskName()).configure(t -> t.dependsOn(generateTask));
		});
	}
}
