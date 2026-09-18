# native-hint (prototipo)

Escanea código fuente Java buscando tipos que Spring AOT **no** puede descubrir solo, y que por
lo tanto van a fallar en runtime bajo GraalVM native-image con
`UnsupportedFeatureError: Record components not available` — nunca en tests, porque ahí no corre
como native image.

Casos detectados hoy:

- `rabbitTemplate.convertAndSend(..., payload)` / `eventPublisher.publishEvent(...)` — el payload
  viaja como `Object` genérico y nunca aparece en la firma de un `@RestController`, así que el
  escaneo AOT de Spring MVC no lo alcanza.
- Parámetros de métodos `@RabbitListener` / `@MessageMapping` / `@EventListener` — mismo problema,
  el tipo del mensaje no pasa por el escaneo MVC.
- Parámetros anotados `@ParameterObject` (springdoc-openapi) — bindean un record/DTO completo
  desde query params, pero nunca son el tipo de RETORNO de ningún controller.
- Tipo de retorno de métodos `@HttpExchange`/`@GetExchange`/`@PostExchange`/etc. (clientes HTTP
  declarativos) — se deserializa desde la respuesta JSON sin pasar nunca por un controller propio.
  Desenrolla `List<T>`, `ResponseEntity<T>`, `Optional<T>`, etc.
- Clases `implements ConstraintValidator<...>` — Bean Validation las instancia por reflection.
- Referencias `T(fully.qualified.Type)` dentro de expresiones SpEL escritas como string
  (`@PreAuthorize`, `@Value`, etc.) — para el compilador es texto, no código.
- **Expansión recursiva de campos anidados**: si un tipo detectado tiene un campo cuyo tipo es
  otro record/DTO propio del proyecto, ese tipo también se registra, con la profundidad que haga
  falta.
- **Rastreo de genéricos entre clases**: si un campo es de un parámetro de tipo propio (p.ej.
  `T message` en un wrapper `MessageEnvelope<T>`), sigue los call sites que instancian ese wrapper —
  y si el argumento ahí también es demasiado genérico (`Object`), sigue la cadena hacia los call
  sites de ese método, acotado en profundidad.

## Uso como plugin de Gradle (auto-registro, recomendado)

En `settings.gradle` del proyecto que lo va a usar:

```groovy
pluginManagement {
	repositories {
		mavenLocal() // hasta que apruebe el Gradle Plugin Portal
		gradlePluginPortal()
	}
}
```

En `build.gradle`:

```groovy
plugins {
	id 'java'
	id 'io.github.matiasferrerovilas.nativehint' version '1.0.6'
}

repositories {
	mavenLocal() // idem, hasta la aprobación del Portal
	mavenCentral()
}

dependencies {
	// Solo para que @NativeHint resuelva en compilación — retención SOURCE,
	// no queda en el classpath de runtime ni en el jar/native-image final.
	compileOnly 'io.github.matiasferrerovilas:native-hint:1.0.6'
}
```

Y anotar la clase `@SpringBootApplication` (o cualquier otra clase del proyecto) con `@dev.nativehint.NativeHint`:

```java
import dev.nativehint.NativeHint;

@SpringBootApplication
@NativeHint
public class MyApplication {
	public static void main(String[] args) {
		SpringApplication.run(MyApplication.class, args);
	}
}
```

Con eso alcanza. Nada más para instalar, nada para registrar a mano: `hintHunterGenerate` corre automáticamente antes de `compileJava`/`processResources` en cualquier build normal (`build`, `bootRun`, `processAot`, la compilación native de GraalVM), escanea el código, y si encuentra la anotación:

1. Genera `nativehint.generated.NativeHintGeneratedRegistrar` (un `RuntimeHintsRegistrar` real, compilable, con los tipos detectados) en `build/generated/sources/nativeHint/java/main`.
2. Genera `META-INF/spring/aot.factories` apuntando a esa clase, así Spring la descubre sola — sin `@ImportRuntimeHints` manual.

**Sin la anotación en ningún lado, el plugin no genera absolutamente nada** — aplicarlo no cambia el build hasta que se opta explícitamente por la feature. Si algún tipo detectado no se pudo resolver a nombre completo (dependencia externa fuera del classpath conocido, tipo genérico, etc.), se loguea como warning en vez de romper el build silenciosamente, y hay que registrarlo a mano.

## Solo reportar, sin generar nada (`hintHunterScan`)

Para auditar sin tocar el build (por ejemplo en CI, para ver qué encontraría sin comprometerse a generar código):

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

Validado de punta a punta contra código real (dos servicios Spring Boot + GraalVM native-image en
producción, sin relación entre sí, y un proyecto de prueba desde cero): detecta exactamente los
mismos tipos que ya estaban registrados a mano en un `RuntimeHintsRegistrar` escrito manualmente,
sin falsos positivos, y `./gradlew processAot` corre limpio — Spring descubre y ejecuta el
`RuntimeHintsRegistrar` generado sin `ClassNotFoundException` ni ningún otro error. En uno de esos
proyectos, el archivo escrito a mano (16 tipos) quedó completamente reemplazado por el generado
automáticamente (42 tipos, incluyendo campos anidados y un caso de genéricos entre clases que el
desarrollador ni siquiera tenía registrado).

Resolución de tipos vía `JavaSymbolSolver` (raíz de fuentes escaneada + reflection para el JDK),
con fallback a heurística de AST cuando el solver no puede resolver algo (dependencia fuera del
classpath conocido, etc.).

Gotcha real encontrado y resuelto: si el `build.gradle` del consumidor tiene su propio
`sourceSets { main { java { srcDirs = [...] } } }` con `=` (común en proyectos generados por
Spring Initializr), esa asignación pisa cualquier `srcDir()` agregado durante `apply()` del
plugin. Se resolvió enganchando el `srcDir()` del plugin dentro de `project.afterEvaluate {}`, que
corre después de que el script completo del consumidor ya se evaluó.

Próximos pasos: sumar el classpath compilado del proyecto objetivo (jars de dependencias) al type
solver para resolver también tipos externos, y cubrir más frameworks de mensajería (Kafka, JMS,
WebSocket STOMP) además de RabbitMQ.
