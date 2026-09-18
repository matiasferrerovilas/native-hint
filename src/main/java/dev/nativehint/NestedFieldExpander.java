package dev.nativehint;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Un tipo detectado (p.ej. {@code OrderRecord}) puede tener campos cuyo tipo es OTRO
 * record/DTO propio del proyecto (p.ej. {@code OrderRecord.Metadata}) — Jackson necesita
 * reflection para ESE tipo también al serializar/deserializar el objeto completo, aunque nunca
 * aparezca directamente en ninguna llamada a convertAndSend/publishEvent/etc. Este expansor sigue
 * los campos recursivamente (con límite de profundidad y detección de ciclos) para que el
 * registrador generado no dependa de que el desarrollador se acuerde de agregar los tipos
 * anidados a mano.
 *
 * <p>Solo puede expandir tipos cuyo código fuente está en la raíz escaneada (no hay bytecode de
 * dependencias externas en el {@link CombinedTypeSolver}) — para un campo de una librería externa
 * simplemente no baja más, lo cual es el comportamiento correcto: esos tipos ya tienen sus propios
 * hints (o no los necesitan) provistos por su propia librería.
 */
public class NestedFieldExpander {

	private static final Set<String> WRAPPER_TYPES = Set.of(
			"java.util.List", "java.util.Set", "java.util.Collection", "java.util.Iterable",
			"java.util.Optional", "java.util.concurrent.CompletableFuture",
			"org.springframework.http.ResponseEntity", "reactor.core.publisher.Mono", "reactor.core.publisher.Flux"
	);
	private static final int MAX_VISITED = 500;

	private final CombinedTypeSolver typeSolver;
	private final GenericFieldResolver genericFieldResolver;

	public NestedFieldExpander(CombinedTypeSolver typeSolver, List<CompilationUnit> allUnits) {
		this.typeSolver = typeSolver;
		this.genericFieldResolver = new GenericFieldResolver(allUnits);
	}

	public Set<String> expand(Set<String> seedFqns) {
		Set<String> visited = new LinkedHashSet<>();
		Deque<String> pending = new ArrayDeque<>(seedFqns);
		while (!pending.isEmpty() && visited.size() < MAX_VISITED) {
			String fqn = pending.poll();
			if (!visited.add(fqn)) {
				continue;
			}
			for (String nested : nestedFieldTypesOf(fqn)) {
				if (!visited.contains(nested)) {
					pending.add(nested);
				}
			}
		}
		return visited;
	}

	private Set<String> nestedFieldTypesOf(String fqn) {
		Set<String> nested = new LinkedHashSet<>();
		ResolvedReferenceTypeDeclaration declaration;
		try {
			declaration = typeSolver.solveType(fqn);
		} catch (RuntimeException e) {
			// No hay fuente disponible para este tipo (dependencia externa, tipo del JDK, etc.) —
			// no hay más para expandir acá.
			return nested;
		}
		List<ResolvedFieldDeclaration> fields;
		try {
			fields = declaration.getDeclaredFields();
		} catch (RuntimeException e) {
			return nested;
		}
		String simpleName = fqn.contains(".") ? fqn.substring(fqn.lastIndexOf('.') + 1) : fqn;
		for (int i = 0; i < fields.size(); i++) {
			ResolvedFieldDeclaration field = fields.get(i);
			try {
				ResolvedType fieldType = field.getType();
				if (fieldType.isTypeVariable()) {
					// El campo es de un parámetro de tipo propio del record/clase (p.ej. "T
					// message" en MessageEnvelope<T>) — el tipo real solo se sabe en el call site que
					// instancia esta clase, no en la propia declaración del campo.
					nested.addAll(genericFieldResolver.resolveConcreteTypesAtConstructorArgument(simpleName, i));
				} else {
					collectLeafTypes(fieldType, nested);
				}
			} catch (RuntimeException e) {
				// Tipo de campo no resoluble (genérico complejo, etc.) — se ignora ese campo.
			}
		}
		return nested;
	}

	private void collectLeafTypes(ResolvedType type, Set<String> out) {
		if (!type.isReferenceType()) {
			return;
		}
		ResolvedReferenceType refType = type.asReferenceType();
		String qualifiedName = refType.getQualifiedName();
		if (WRAPPER_TYPES.contains(qualifiedName)) {
			for (ResolvedType typeArgument : refType.typeParametersValues()) {
				collectLeafTypes(typeArgument, out);
			}
			return;
		}
		if (qualifiedName.startsWith("java.")) {
			return;
		}
		out.add(qualifiedName);
	}
}
