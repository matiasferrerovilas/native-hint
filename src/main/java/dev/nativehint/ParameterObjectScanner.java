package dev.nativehint;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * Detecta parámetros anotados {@code @ParameterObject} (springdoc-openapi): bindea un record/DTO
 * completo desde query params en un método de {@code @RestController}. Spring MVC solo escanea
 * tipos de RETORNO de un controller para AOT — un tipo que aparece únicamente como parámetro de
 * entrada (nunca se devuelve) queda sin reflection registrada y explota en runtime bajo
 * native-image.
 */
public class ParameterObjectScanner {

	private static final String TARGET_ANNOTATION = "ParameterObject";

	public List<HintCandidate> scan(CompilationUnit cu, String fileName) {
		List<HintCandidate> found = new ArrayList<>();
		cu.accept(new VoidVisitorAdapter<Void>() {
			@Override
			public void visit(Parameter p, Void arg) {
				super.visit(p, arg);
				if (p.getAnnotationByName(TARGET_ANNOTATION).isEmpty()) {
					return;
				}
				String typeName = resolveType(p);
				found.add(new HintCandidate(typeName, HintCandidate.Reason.PARAMETER_OBJECT_BINDING,
						fileName, p.getBegin().map(pos -> pos.line).orElse(-1), p.toString()));
			}
		}, null);
		return found;
	}

	private String resolveType(Parameter p) {
		try {
			return p.getType().resolve().describe();
		} catch (RuntimeException e) {
			return p.getTypeAsString();
		}
	}
}
