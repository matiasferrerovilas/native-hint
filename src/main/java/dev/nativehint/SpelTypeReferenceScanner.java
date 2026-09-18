package dev.nativehint;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.StringLiteralExpr;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detecta referencias {@code T(fully.qualified.Type)} dentro de expresiones SpEL escritas como
 * literales de string (p.ej. {@code @PreAuthorize}, {@code @Value}, {@code @ConditionalOnExpression}).
 * Spring AOT no las ve porque, para el compilador, son texto plano — no código.
 */
public class SpelTypeReferenceScanner {

	private static final Pattern SPEL_TYPE_REF = Pattern.compile("T\\(([\\w.]+)\\)");

	public List<HintCandidate> scan(CompilationUnit cu, String fileName) {
		List<HintCandidate> found = new ArrayList<>();
		cu.findAll(StringLiteralExpr.class).forEach(literal -> {
			Matcher matcher = SPEL_TYPE_REF.matcher(literal.getValue());
			while (matcher.find()) {
				found.add(new HintCandidate(matcher.group(1), HintCandidate.Reason.SPEL_TYPE_REFERENCE,
						fileName, literal.getBegin().map(p -> p.line).orElse(-1), literal.getValue()));
			}
		});
		return found;
	}
}
