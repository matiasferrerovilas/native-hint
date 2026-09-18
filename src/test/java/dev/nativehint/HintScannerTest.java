package dev.nativehint;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HintScannerTest {

	private static final Path FIXTURE = Path.of("src/test/resources/fixtures/sample/SampleRabbitPublisher.java");

	@Test
	void resolvesConvertAndSendPayloadPassedAsVariable() {
		List<HintCandidate> found = new HintScanner().scanDirectory(FIXTURE.getParent());
		assertTrue(found.stream().anyMatch(c -> c.reason() == HintCandidate.Reason.RABBIT_CONVERT_AND_SEND
				&& c.typeName().equals("TicketCreatedEvent")));
	}

	@Test
	void resolvesConvertAndSendPayloadConstructedInline() {
		List<HintCandidate> found = new HintScanner().scanDirectory(FIXTURE.getParent());
		assertTrue(found.stream().anyMatch(c -> c.reason() == HintCandidate.Reason.RABBIT_CONVERT_AND_SEND
				&& c.typeName().equals("TicketClosedEvent")));
	}

	@Test
	void detectsRabbitListenerParameterType() {
		List<HintCandidate> found = new HintScanner().scanDirectory(FIXTURE.getParent());
		assertTrue(found.stream().anyMatch(c -> c.reason() == HintCandidate.Reason.RABBIT_OR_STOMP_LISTENER_PARAMETER
				&& c.typeName().equals("TicketAssignedEvent")));
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

	private static final Path EXTENDED_FIXTURE = Path.of("src/test/resources/fixtures/extended");

	@Test
	void detectsApplicationEventPublisherPayload() {
		List<HintCandidate> found = new HintScanner().scanDirectory(EXTENDED_FIXTURE);
		assertTrue(found.stream().anyMatch(c -> c.reason() == HintCandidate.Reason.APPLICATION_EVENT_PUBLISHED
				&& c.typeName().contains("NotificationEvent")));
	}

	@Test
	void detectsEventListenerParameter() {
		List<HintCandidate> found = new HintScanner().scanDirectory(EXTENDED_FIXTURE);
		assertTrue(found.stream().anyMatch(c -> c.reason() == HintCandidate.Reason.EVENT_LISTENER_PARAMETER
				&& c.typeName().contains("NotificationEvent")));
	}

	@Test
	void detectsParameterObjectBinding() {
		List<HintCandidate> found = new HintScanner().scanDirectory(EXTENDED_FIXTURE);
		assertTrue(found.stream().anyMatch(c -> c.reason() == HintCandidate.Reason.PARAMETER_OBJECT_BINDING
				&& c.typeName().contains("SearchFilterRecord")));
	}

	@Test
	void detectsHttpExchangeReturnTypeUnwrappingList() {
		List<HintCandidate> found = new HintScanner().scanDirectory(EXTENDED_FIXTURE);
		assertTrue(found.stream().anyMatch(c -> c.reason() == HintCandidate.Reason.HTTP_EXCHANGE_RETURN_TYPE
				&& c.typeName().contains("UserProfile")));
	}

	@Test
	void detectsConstraintValidatorClass() {
		List<HintCandidate> found = new HintScanner().scanDirectory(EXTENDED_FIXTURE);
		assertTrue(found.stream().anyMatch(c -> c.reason() == HintCandidate.Reason.CONSTRAINT_VALIDATOR
				&& c.typeName().contains("PositiveAmountValidator")));
	}

	@Test
	void expandsNestedFieldTypesTransitivelyThroughRecordsAndGenerics() {
		Path nestedRoot = Path.of("src/test/resources/fixtures/nested");
		Set<String> expanded = new HintScanner().expandNestedFieldTypes(
				Set.of("com.example.nested.OuterRecord"), List.of(nestedRoot));

		assertTrue(expanded.contains("com.example.nested.OuterRecord"));
		assertTrue(expanded.contains("com.example.nested.InnerRecord"));
		assertTrue(expanded.contains("com.example.nested.DeeplyNestedRecord"));
	}

	@Test
	void tracesGenericWrapperPayloadThroughCallSitesAcrossClasses() {
		Path genericsRoot = Path.of("src/test/resources/fixtures/generics");
		Set<String> expanded = new HintScanner().expandNestedFieldTypes(
				Set.of("com.example.generics.MessageEnvelope"), List.of(genericsRoot));

		assertTrue(expanded.contains("com.example.generics.CustomerProfile"),
				"Debería rastrear MessageEnvelope.message (tipo T) -> publish(Object result) "
						+ "-> this.publish(customerProfile) -> CustomerProfile");
	}
}
