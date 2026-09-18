package dev.nativehint;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Detecta parámetros de métodos {@code @RabbitListener} / {@code @MessageMapping} /
 * {@code @EventListener}: el payload deserializado tampoco pasa por el escaneo de Spring MVC
 * porque no es el tipo de retorno de un {@code @RestController}, así que también necesita hint
 * explícito bajo native-image.
 */
public class ListenerParameterScanner {

	/** Anotaciones de método cuyos parámetros son un payload deserializado fuera del escaneo MVC. */
	private enum ListenerAnnotation {
		RABBIT_LISTENER("RabbitListener", HintCandidate.Reason.RABBIT_OR_STOMP_LISTENER_PARAMETER),
		MESSAGE_MAPPING("MessageMapping", HintCandidate.Reason.RABBIT_OR_STOMP_LISTENER_PARAMETER),
		EVENT_LISTENER("EventListener", HintCandidate.Reason.EVENT_LISTENER_PARAMETER);

		private final String annotationName;
		private final HintCandidate.Reason reason;

		ListenerAnnotation(String annotationName, HintCandidate.Reason reason) {
			this.annotationName = annotationName;
			this.reason = reason;
		}

		static Optional<HintCandidate.Reason> reasonFor(String annotationName) {
			return Arrays.stream(values())
					.filter(a -> a.annotationName.equals(annotationName))
					.map(a -> a.reason)
					.findFirst();
		}
	}

	private static final Set<String> IGNORED_TYPES = Set.of("String", "Message", "byte[]", "Map", "Object");

	public List<HintCandidate> scan(CompilationUnit cu, String fileName) {
		List<HintCandidate> found = new ArrayList<>();
		cu.accept(new VoidVisitorAdapter<Void>() {
			@Override
			public void visit(MethodDeclaration method, Void arg) {
				super.visit(method, arg);
				Optional<HintCandidate.Reason> reason = method.getAnnotations().stream()
						.map(a -> ListenerAnnotation.reasonFor(a.getNameAsString()))
						.flatMap(Optional::stream)
						.findFirst();
				if (reason.isEmpty()) {
					return;
				}
				for (Parameter p : method.getParameters()) {
					String rawTypeName = p.getTypeAsString();
					if (IGNORED_TYPES.contains(rawTypeName)) {
						continue;
					}
					String typeName = resolveType(p, rawTypeName);
					found.add(new HintCandidate(typeName, reason.get(),
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
