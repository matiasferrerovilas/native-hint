package dev.nativehint;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Detecta el tipo publicado en llamadas a métodos "de payload genérico" — el argumento viaja como
 * {@code Object}, así que nunca aparece en la firma de un {@code @RestController} y el escaneo AOT
 * de Spring MVC no lo alcanza:
 * <ul>
 *   <li>{@code rabbitTemplate.convertAndSend(...)} — mensajería AMQP/WebSocket-STOMP.</li>
 *   <li>{@code eventPublisher.publishEvent(...)} — el bus de eventos in-process de Spring.</li>
 * </ul>
 * Resuelve el tipo estático del último argumento con el symbol solver, con fallback a heurística
 * de AST (constructor directo, variable local, parámetro de método o campo de la clase) cuando el
 * solver no puede.
 */
public class MethodCallPayloadScanner {

	/** Métodos cuyo último argumento es un payload que Spring AOT no puede descubrir por sí solo. */
	private enum GenericPayloadMethod {
		CONVERT_AND_SEND("convertAndSend", HintCandidate.Reason.RABBIT_CONVERT_AND_SEND),
		PUBLISH_EVENT("publishEvent", HintCandidate.Reason.APPLICATION_EVENT_PUBLISHED);

		private final String methodName;
		private final HintCandidate.Reason reason;

		GenericPayloadMethod(String methodName, HintCandidate.Reason reason) {
			this.methodName = methodName;
			this.reason = reason;
		}

		static Optional<HintCandidate.Reason> reasonFor(String methodName) {
			return Arrays.stream(values())
					.filter(m -> m.methodName.equals(methodName))
					.map(m -> m.reason)
					.findFirst();
		}
	}

	public List<HintCandidate> scan(CompilationUnit cu, String fileName) {
		List<HintCandidate> found = new ArrayList<>();
		cu.accept(new VoidVisitorAdapter<Void>() {
			@Override
			public void visit(MethodCallExpr call, Void arg) {
				super.visit(call, arg);
				Optional<HintCandidate.Reason> reason = GenericPayloadMethod.reasonFor(call.getNameAsString());
				if (reason.isEmpty() || call.getArguments().isEmpty()) {
					return;
				}
				Expression payload = call.getArgument(call.getArguments().size() - 1);
				resolveType(payload).ifPresentOrElse(
						typeName -> found.add(new HintCandidate(typeName, reason.get(),
								fileName, call.getBegin().map(p -> p.line).orElse(-1), call.toString())),
						() -> found.add(new HintCandidate("<unresolved: " + payload + ">",
								reason.get(), fileName,
								call.getBegin().map(p -> p.line).orElse(-1), call.toString())));
			}
		}, null);
		return found;
	}

	private Optional<String> resolveType(Expression payload) {
		try {
			return Optional.of(payload.calculateResolvedType().describe());
		} catch (RuntimeException e) {
			// El symbol solver no pudo resolverlo (dependencia fuera del classpath conocido,
			// tipo genérico, etc.) — caemos a la heurística de AST de abajo.
		}
		return switch (payload) {
			case ObjectCreationExpr creation -> Optional.of(creation.getType().asString());
			case NameExpr nameExpr -> resolveSimpleName(nameExpr, nameExpr.getNameAsString());
			case FieldAccessExpr fieldAccess -> resolveSimpleName(fieldAccess, fieldAccess.getNameAsString());
			default -> Optional.empty();
		};
	}

	private Optional<String> resolveSimpleName(Node context, String name) {
		Optional<MethodDeclaration> method = context.findAncestor(MethodDeclaration.class);
		if (method.isPresent()) {
			for (Parameter p : method.get().getParameters()) {
				if (p.getNameAsString().equals(name)) {
					return Optional.of(p.getTypeAsString());
				}
			}
			for (VariableDeclarator v : method.get().findAll(VariableDeclarator.class)) {
				if (v.getNameAsString().equals(name)) {
					return Optional.of(v.getTypeAsString());
				}
			}
		}
		Optional<TypeDeclaration> type = context.findAncestor(TypeDeclaration.class);
		if (type.isPresent()) {
			for (FieldDeclaration f : type.get().findAll(FieldDeclaration.class)) {
				for (VariableDeclarator v : f.getVariables()) {
					if (v.getNameAsString().equals(name)) {
						return Optional.of(v.getTypeAsString());
					}
				}
			}
		}
		return Optional.empty();
	}
}
