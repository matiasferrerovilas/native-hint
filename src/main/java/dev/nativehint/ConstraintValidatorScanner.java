package dev.nativehint;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Detecta clases {@code implements ConstraintValidator<Anotacion, Tipo>} (Bean Validation
 * custom): a diferencia de los demás detectores, acá lo que necesita reflection es la propia
 * clase validadora (Spring/Hibernate Validator la instancia por reflection, nunca aparece en la
 * firma de ningún controller), no un tipo de datos.
 */
public class ConstraintValidatorScanner {

	private static final String TARGET_INTERFACE = "ConstraintValidator";

	public List<HintCandidate> scan(CompilationUnit cu, String fileName) {
		List<HintCandidate> found = new ArrayList<>();
		cu.accept(new VoidVisitorAdapter<Void>() {
			@Override
			public void visit(ClassOrInterfaceDeclaration decl, Void arg) {
				super.visit(decl, arg);
				boolean implementsValidator = decl.getImplementedTypes().stream()
						.anyMatch(t -> t.getNameAsString().equals(TARGET_INTERFACE));
				if (!implementsValidator) {
					return;
				}
				String typeName = resolveDeclaringType(cu, decl);
				found.add(new HintCandidate(typeName, HintCandidate.Reason.CONSTRAINT_VALIDATOR,
						fileName, decl.getBegin().map(pos -> pos.line).orElse(-1), decl.getNameAsString()));
			}
		}, null);
		return found;
	}

	private String resolveDeclaringType(CompilationUnit cu, ClassOrInterfaceDeclaration decl) {
		try {
			return decl.resolve().getQualifiedName();
		} catch (RuntimeException e) {
			Optional<String> packageName = cu.getPackageDeclaration().map(NodeWithName::getNameAsString);
			return packageName.map(p -> p + "." + decl.getNameAsString()).orElse(decl.getNameAsString());
		}
	}
}
