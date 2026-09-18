package dev.nativehint;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Detecta parámetros de métodos {@code @RabbitListener} / {@code @MessageMapping}: el payload
 * deserializado tampoco pasa por el escaneo de Spring MVC porque no es el tipo de retorno de un
 * {@code @RestController}, así que también necesita hint explícito bajo native-image.
 */
public class ListenerParameterScanner {

	private static final Set<String> LISTENER_ANNOTATIONS = Set.of("RabbitListener", "MessageMapping");
	private static final Set<String> IGNORED_TYPES = Set.of("String", "Message", "byte[]", "Map", "Object");

	public List<HintCandidate> scan(CompilationUnit cu, String fileName) {
		List<HintCandidate> found = new ArrayList<>();
		cu.accept(new VoidVisitorAdapter<Void>() {
			@Override
			public void visit(MethodDeclaration method, Void arg) {
				super.visit(method, arg);
				boolean isListener = method.getAnnotations().stream()
						.anyMatch(a -> LISTENER_ANNOTATIONS.contains(a.getNameAsString()));
				if (!isListener) {
					return;
				}
				for (Parameter p : method.getParameters()) {
					String rawTypeName = p.getTypeAsString();
					if (IGNORED_TYPES.contains(rawTypeName)) {
						continue;
					}
					String typeName = resolveType(p, rawTypeName);
					found.add(new HintCandidate(typeName, HintCandidate.Reason.RABBIT_OR_STOMP_LISTENER_PARAMETER,
							fileName, method.getBegin().map(pos -> pos.line).orElse(-1),
							method.getDeclarationAsString(false, false, false)));
				}
			}
		}, null);
		return found;
	}

	private String resolveType(Parameter p, String fallback) {
		try {
			return p.getType().resolve().describe();
		} catch (RuntimeException e) {
			return fallback;
		}
	}
}
