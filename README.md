# native-hint (prototipo)

Escanea código fuente Java buscando tipos que Spring AOT **no** puede descubrir solo, y que por
lo tanto van a fallar en runtime bajo GraalVM native-image con
`UnsupportedFeatureError: Record components not available` — nunca en tests, porque ahí no corre
como native image.

Casos detectados hoy:

- `rabbitTemplate.convertAndSend(..., payload)` — el payload viaja como `Object` genérico y nunca
  aparece en la firma de un `@RestController`, así que el escaneo AOT de Spring MVC no lo alcanza.
- Parámetros de métodos `@RabbitListener` / `@MessageMapping` — mismo problema, el tipo del
  mensaje no pasa por el escaneo MVC.
- Referencias `T(fully.qualified.Type)` dentro de expresiones SpEL escritas como string
  (`@PreAuthorize`, `@Value`, etc.) — para el compilador es texto, no código.

## Uso como plugin de Gradle

En `settings.gradle` del proyecto que lo va a usar:

```groovy
pluginManagement {
	repositories {
		mavenLocal() // hasta que se publique al Gradle Plugin Portal
		gradlePluginPortal()
	}
}
```

En `build.gradle`:

```groovy
plugins {
	id 'java'
	id 'dev.nativehint' version '0.1.0-SNAPSHOT'
}
```

Y correr:

```
./gradlew hintHunterScan
```

## Uso como CLI standalone

```
./gradlew run --args="/ruta/a/src/main/java"
```

Imprime los candidatos encontrados y un `RuntimeHintsRegistrar` de ejemplo listo para pegar
(ajustando el paquete e imports).

## Estado

Prueba de concepto. Validado contra el código real de `api-identity`: detecta exactamente los
3 records (`InvitationCreatedEvent`, `InvitationAcceptedEvent`, `MemberRemovedEvent`) que ya
estaban registrados a mano en `WebBindingRuntimeHints`, sin falsos positivos.

Resolución de tipos vía `JavaSymbolSolver` (raíz de fuentes escaneada + reflection para el JDK),
con fallback a heurística de AST cuando el solver no puede resolver algo (dependencia fuera del
classpath conocido, etc.). Esto ya da nombres completamente calificados sin depender del estilo de
código de un proyecto en particular — pensado para funcionar en cualquier proyecto Spring, no solo
en los repos donde se probó.

Próximos pasos: sumar el classpath compilado del proyecto objetivo (jars de dependencias) al type
solver para resolver también tipos externos, cubrir más frameworks de mensajería (Kafka, JMS,
WebSocket STOMP) además de RabbitMQ, y generar el `RuntimeHintsRegistrar` como archivo `.java`
compilable en vez de solo imprimirlo por stdout.
