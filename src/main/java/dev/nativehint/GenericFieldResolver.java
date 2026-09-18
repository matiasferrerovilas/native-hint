package dev.nativehint;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Cuando un campo de un record/clase es de un tipo genérico propio (p.ej. {@code T message} en
 * {@code MessageEnvelope<T>}), el tipo real solo se conoce en el call site que instancia ese tipo
 * (p.ej. {@code new MessageEnvelope<>(eventType, result)}). Si ESE argumento también es "demasiado
 * genérico" — un parámetro de método declarado {@code Object} — hay que seguir la cadena hasta los
 * call sites de ESE método, y así sucesivamente. Busca en todo el proyecto ya parseado, acotado en
 * profundidad para no correr indefinidamente ante cadenas de llamadas cíclicas.
 *
 * <p>Es una heurística de mejor esfuerzo, no un análisis de flujo de datos completo: no resuelve
 * sobrecargas de métodos, ni distingue el método correcto cuando hay varios con el mismo nombre y
 * misma cantidad de argumentos en clases no relacionadas. Para el patrón que motivó esto (un
 * método "publish"/"send" genérico reenviado desde varios call sites) alcanza.
 */
public class GenericFieldResolver {

	private static final int MAX_HOPS = 4;

	private final List<CompilationUnit> allUnits;

	public GenericFieldResolver(List<CompilationUnit> allUnits) {
		this.allUnits = allUnits;
	}

	/**
	 * Busca {@code new <declaringSimpleName><>(...)} en todo el proyecto y devuelve los tipos
	 * concretos que efectivamente llegan al argumento en la posición {@code paramIndex}.
	 */
	public Set<String> resolveConcreteTypesAtConstructorArgument(String declaringSimpleName, int paramIndex) {
		Set<String> results = new LinkedHashSet<>();
		for (CompilationUnit cu : allUnits) {
			for (ObjectCreationExpr creation : cu.findAll(ObjectCreationExpr.class)) {
				if (!creation.getType().getNameAsString().equals(declaringSimpleName)
						|| paramIndex >= creation.getArguments().size()) {
					continue;
				}
				results.addAll(resolveExpressionConcreteTypes(creation.getArgument(paramIndex), new HashSet<>(), 0));
			}
		}
		return results;
	}

	private Set<String> resolveExpressionConcreteTypes(Expression expr, Set<String> visited, int hops) {
		Set<String> results = new LinkedHashSet<>();
		if (hops > MAX_HOPS) {
			return results;
		}

		String resolved = tryResolve(expr);
		if (resolved != null && !isGenericPlaceholder(resolved)) {
			results.add(resolved);
			return results;
		}

		Optional<ParamRef> paramRef = asMethodParameterReference(expr);
		if (paramRef.isEmpty()) {
			return results;
		}
		ParamRef ref = paramRef.get();
		if (!visited.add(ref.methodName + "#" + ref.paramIndex)) {
			return results;
		}

		for (CompilationUnit cu : allUnits) {
			for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
				if (!call.getNameAsString().equals(ref.methodName) || ref.paramIndex >= call.getArguments().size()) {
					continue;
				}
				results.addAll(resolveExpressionConcreteTypes(call.getArgument(ref.paramIndex), visited, hops + 1));
			}
		}
		return results;
	}

	private boolean isGenericPlaceholder(String typeName) {
		return typeName.equals("java.lang.Object") || typeName.equals("Object");
	}

	private String tryResolve(Expression expr) {
		try {
			return expr.calculateResolvedType().describe();
		} catch (RuntimeException e) {
			return null;
		}
	}

	private Optional<ParamRef> asMethodParameterReference(Expression expr) {
		if (!(expr instanceof NameExpr nameExpr)) {
			return Optional.empty();
		}
		Optional<MethodDeclaration> method = expr.findAncestor(MethodDeclaration.class);
		if (method.isEmpty()) {
			return Optional.empty();
		}
		List<Parameter> params = method.get().getParameters();
		for (int i = 0; i < params.size(); i++) {
			if (params.get(i).getNameAsString().equals(nameExpr.getNameAsString())) {
				return Optional.of(new ParamRef(method.get().getNameAsString(), i));
			}
		}
		return Optional.empty();
	}

	private record ParamRef(String methodName, int paramIndex) {
	}
}
