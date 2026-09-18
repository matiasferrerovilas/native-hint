package dev.nativehint;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Detecta el tipo de retorno de métodos de un cliente HTTP declarativo (
 * {@code @HttpExchange}/{@code @GetExchange}/{@code @PostExchange}/{@code @PutExchange}/
 * {@code @DeleteExchange}/{@code @PatchExchange}): ese tipo se deserializa desde la respuesta
 * JSON, pero nunca pasa por un {@code @RestController} de este servicio, así que el escaneo AOT
 * de Spring MVC no lo alcanza. Desenrolla wrappers comunes ({@code List<T>},
 * {@code ResponseEntity<T>}, {@code Optional<T>}, etc.) para llegar al tipo real.
 */
public class HttpExchangeReturnTypeScanner {

	/** Anotaciones de Spring 6 que declaran un método de cliente HTTP declarativo. */
	private enum HttpExchangeAnnotation {
		HTTP_EXCHANGE("HttpExchange"),
		GET_EXCHANGE("GetExchange"),
		POST_EXCHANGE("PostExchange"),
		PUT_EXCHANGE("PutExchange"),
		DELETE_EXCHANGE("DeleteExchange"),
		PATCH_EXCHANGE("PatchExchange");

		private final String simpleName;

		HttpExchangeAnnotation(String simpleName) {
			this.simpleName = simpleName;
		}

		static boolean matches(String annotationSimpleName) {
			return Arrays.stream(values()).anyMatch(a -> a.simpleName.equals(annotationSimpleName));
		}
	}

	/** Wrappers genéricos comunes a desenrollar para llegar al tipo real que se deserializa. */
	private enum GenericWrapperType {
		RESPONSE_ENTITY("ResponseEntity"),
		LIST("List"),
		COLLECTION("Collection"),
		SET("Set"),
		ITERABLE("Iterable"),
		OPTIONAL("Optional"),
		MONO("Mono"),
		FLUX("Flux"),
		COMPLETABLE_FUTURE("CompletableFuture");

		private final String simpleName;

		GenericWrapperType(String simpleName) {
			this.simpleName = simpleName;
		}

		static boolean matches(String typeSimpleName) {
			return Arrays.stream(values()).anyMatch(w -> w.simpleName.equals(typeSimpleName));
		}
	}

	private static final Set<String> IGNORED_SIMPLE_NAMES = Set.of("void", "Void", "String", "Object");

	public List<HintCandidate> scan(CompilationUnit cu, String fileName) {
		List<HintCandidate> found = new ArrayList<>();
		cu.accept(new VoidVisitorAdapter<Void>() {
			@Override
			public void visit(MethodDeclaration method, Void arg) {
				super.visit(method, arg);
				boolean isHttpExchangeMethod = method.getAnnotations().stream()
						.anyMatch(a -> HttpExchangeAnnotation.matches(a.getNameAsString()));
				if (!isHttpExchangeMethod) {
					return;
				}
				List<Type> leaves = new ArrayList<>();
				collectLeafTypes(method.getType(), leaves);
				for (Type leaf : leaves) {
					String typeName = resolveType(leaf);
					if (isIgnored(typeName)) {
						continue;
					}
					found.add(new HintCandidate(typeName, HintCandidate.Reason.HTTP_EXCHANGE_RETURN_TYPE,
							fileName, method.getBegin().map(pos -> pos.line).orElse(-1),
							method.getDeclarationAsString(false, false, false)));
				}
			}
		}, null);
		return found;
	}

	private void collectLeafTypes(Type type, List<Type> leaves) {
		if (type instanceof ClassOrInterfaceType coi && coi.getTypeArguments().isPresent()
				&& GenericWrapperType.matches(coi.getNameAsString())) {
			for (Type argument : coi.getTypeArguments().get()) {
				collectLeafTypes(argument, leaves);
			}
			return;
		}
		leaves.add(type);
	}

	private String resolveType(Type type) {
		try {
			return type.resolve().describe();
		} catch (RuntimeException e) {
			return type.asString();
		}
	}

	private boolean isIgnored(String typeName) {
		if (typeName.startsWith("java.") || typeName.contains("<")) {
			return true;
		}
		String simpleName = typeName.contains(".") ? typeName.substring(typeName.lastIndexOf('.') + 1) : typeName;
		return IGNORED_SIMPLE_NAMES.contains(simpleName) || simpleName.equals(simpleName.toLowerCase());
	}
}
