package dev.nativehint;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class HintHunter {

	public static void main(String[] args) {
		if (args.length != 1) {
			System.err.println("Uso: hint-hunter <directorio-de-fuentes-java>");
			System.exit(1);
		}
		Path sourceRoot = Path.of(args[0]);
		List<HintCandidate> candidates = new HintScanner().scanDirectory(sourceRoot);

		if (candidates.isEmpty()) {
			System.out.println("No se encontraron candidatos a runtime hints.");
			return;
		}

		System.out.println("Candidatos a runtime hints (" + candidates.size() + "):\n");
		for (HintCandidate c : candidates) {
			System.out.printf("[%s] %s:%d -> %s%n", c.reason(), c.file(), c.line(), c.typeName());
		}

		Set<String> resolvedTypes = new LinkedHashSet<>();
		for (HintCandidate c : candidates) {
			if (!c.typeName().startsWith("<unresolved")) {
				resolvedTypes.add(c.typeName());
			}
		}

		System.out.println("\n--- RuntimeHintsRegistrar sugerido ---\n");
		System.out.println("public class GeneratedRuntimeHints implements RuntimeHintsRegistrar {");
		System.out.println("    private static final Class<?>[] TYPES = {");
		resolvedTypes.forEach(t -> System.out.println("            " + t + ".class,"));
		System.out.println("    };");
		System.out.println();
		System.out.println("    @Override");
		System.out.println("    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {");
		System.out.println("        for (Class<?> type : TYPES) {");
		System.out.println("            hints.reflection().registerType(type, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,");
		System.out.println("                    MemberCategory.INVOKE_DECLARED_METHODS);");
		System.out.println("        }");
		System.out.println("    }");
		System.out.println("}");
	}
}
