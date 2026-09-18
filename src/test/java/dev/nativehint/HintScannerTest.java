package dev.nativehint;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HintScannerTest {

	private static final Path FIXTURE = Path.of("src/test/resources/fixtures/sample/SampleRabbitPublisher.java");

	@Test
	void resolvesConvertAndSendPayloadPassedAsVariable() {
		List<HintCandidate> found = new HintScanner().scanDirectory(FIXTURE.getParent());
		assertTrue(found.stream().anyMatch(c -> c.reason() == HintCandidate.Reason.RABBIT_CONVERT_AND_SEND
				&& c.typeName().equals("InvitationCreatedEvent")));
	}

	@Test
	void resolvesConvertAndSendPayloadConstructedInline() {
		List<HintCandidate> found = new HintScanner().scanDirectory(FIXTURE.getParent());
		assertTrue(found.stream().anyMatch(c -> c.reason() == HintCandidate.Reason.RABBIT_CONVERT_AND_SEND
				&& c.typeName().equals("MemberRemovedEvent")));
	}

	@Test
	void detectsRabbitListenerParameterType() {
		List<HintCandidate> found = new HintScanner().scanDirectory(FIXTURE.getParent());
		assertTrue(found.stream().anyMatch(c -> c.reason() == HintCandidate.Reason.RABBIT_OR_STOMP_LISTENER_PARAMETER
				&& c.typeName().equals("InvitationAcceptedEvent")));
	}

	@Test
	void detectsSpelTypeReference() {
		List<HintCandidate> found = new HintScanner().scanDirectory(FIXTURE.getParent());
		assertTrue(found.stream().anyMatch(c -> c.reason() == HintCandidate.Reason.SPEL_TYPE_REFERENCE
				&& c.typeName().equals("com.example.security.RoleChecker")));
	}

	@Test
	void findsExactlyFourCandidatesInFixture() {
		List<HintCandidate> found = new HintScanner().scanDirectory(FIXTURE.getParent());
		assertEquals(4, found.size());
	}

	@Test
	void resolvesFullyQualifiedNameWhenTypeIsDeclaredInTheScannedSourceRoot() {
		Path solverProjectRoot = Path.of("src/test/resources/fixtures/solverproject");
		List<HintCandidate> found = new HintScanner().scanDirectory(solverProjectRoot);
		assertTrue(found.stream().anyMatch(c -> c.reason() == HintCandidate.Reason.RABBIT_CONVERT_AND_SEND
				&& c.typeName().equals("com.example.events.OrderCreatedEvent")));
	}

	@Test
	void detectsNativeHintMarkerWhenPresent() {
		Path markerRoot = Path.of("src/test/resources/fixtures/marker");
		assertTrue(new HintScanner().hasNativeHintMarker(markerRoot));
	}

	@Test
	void doesNotDetectNativeHintMarkerWhenAbsent() {
		assertFalse(new HintScanner().hasNativeHintMarker(FIXTURE.getParent()));
	}
}
