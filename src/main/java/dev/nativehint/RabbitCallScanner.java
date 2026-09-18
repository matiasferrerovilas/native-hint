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
import java.util.List;
import java.util.Optional;

/**
 * Detecta el tipo publicado en llamadas {@code rabbitTemplate.convertAndSend(...)}: ese tipo viaja
 * como {@code Object} genérico y nunca aparece en la firma de un {@code @RestController}, así que
 * el escaneo AOT de Spring MVC no lo alcanza (ver WebBindingRuntimeHints.java en api-identity).
 * Resuelve el tipo estático del último argumento sin symbol solver: constructor directo, variable
 * local, parámetro de método o campo de la clase.
 */
public class RabbitCallScanner {

	public List<HintCandidate> scan(CompilationUnit cu, String fileName) {
		List<HintCandidate> found = new ArrayList<>();
		cu.accept(new VoidVisitorAdapter<Void>() {
			@Override
			public void visit(MethodCallExpr call, Void arg) {
				super.visit(call, arg);
				if (!"convertAndSend".equals(call.getNameAsString()) || call.getArguments().isEmpty()) {
					return;
				}
				Expression payload = call.getArgument(call.getArguments().size() - 1);
				resolveType(payload).ifPresentOrElse(
						typeName -> found.add(new HintCandidate(typeName, HintCandidate.Reason.RABBIT_CONVERT_AND_SEND,
								fileName, call.getBegin().map(p -> p.line).orElse(-1), call.toString())),
						() -> found.add(new HintCandidate("<unresolved: " + payload + ">",
								HintCandidate.Reason.RABBIT_CONVERT_AND_SEND, fileName,
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
		if (payload instanceof ObjectCreationExpr creation) {
			return Optional.of(creation.getType().asString());
		}
		if (payload instanceof NameExpr nameExpr) {
			return resolveSimpleName(nameExpr, nameExpr.getNameAsString());
		}
		if (payload instanceof FieldAccessExpr fieldAccess) {
			return resolveSimpleName(fieldAccess, fieldAccess.getNameAsString());
		}
		return Optional.empty();
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
