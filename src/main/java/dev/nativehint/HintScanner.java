package dev.nativehint;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Cada scan arma su propio {@link JavaParser} con un symbol solver apuntando a la raíz de fuentes
 * que se está escaneando, más reflection para tipos del JDK. Así resuelve tipos reales (nombre
 * completo, sigue wildcard imports, etc.) para cualquier proyecto, no solo el propio: no hay
 * estado global ni nada hardcodeado a un paquete en particular.
 */
public class HintScanner {

	private final RabbitCallScanner rabbitCallScanner = new RabbitCallScanner();
	private final ListenerParameterScanner listenerParameterScanner = new ListenerParameterScanner();
	private final SpelTypeReferenceScanner spelTypeReferenceScanner = new SpelTypeReferenceScanner();

	public List<HintCandidate> scanDirectory(Path sourceRoot) {
		JavaParser parser = buildParser(sourceRoot);
		List<HintCandidate> results = new ArrayList<>();
		try (Stream<Path> files = Files.walk(sourceRoot)) {
			files.filter(p -> p.toString().endsWith(".java")).forEach(file -> results.addAll(scanFile(parser, file)));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return results;
	}

	private JavaParser buildParser(Path sourceRoot) {
		CombinedTypeSolver typeSolver = new CombinedTypeSolver();
		typeSolver.add(new ReflectionTypeSolver());
		typeSolver.add(new JavaParserTypeSolver(sourceRoot));
		ParserConfiguration configuration = new ParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
				.setSymbolResolver(new JavaSymbolSolver(typeSolver));
		return new JavaParser(configuration);
	}

	private List<HintCandidate> scanFile(JavaParser parser, Path file) {
		ParseResult<CompilationUnit> result;
		try {
			result = parser.parse(file);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (result.getResult().isEmpty()) {
			System.err.println("No se pudo parsear " + file + ": " + result.getProblems());
			return List.of();
		}
		CompilationUnit cu = result.getResult().get();
		String fileName = file.toString();
		List<HintCandidate> results = new ArrayList<>();
		results.addAll(rabbitCallScanner.scan(cu, fileName));
		results.addAll(listenerParameterScanner.scan(cu, fileName));
		results.addAll(spelTypeReferenceScanner.scan(cu, fileName));
		return results;
	}
}
