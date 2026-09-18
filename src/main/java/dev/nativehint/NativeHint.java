package dev.nativehint;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca opt-in: sin esta anotación en algún lugar del código fuente (típicamente la clase
 * {@code @SpringBootApplication}), el plugin de Gradle no genera ni registra nada — aplicar el
 * plugin por sí solo no cambia el build. Con ella puesta, {@code hintHunterGenerate} escanea el
 * código y genera el {@code RuntimeHintsRegistrar} automáticamente en cada build.
 *
 * <p>Retención {@code SOURCE}: es pura señal para el build, no necesita existir en el classpath
 * de runtime ni empaquetarse en el jar/native-image final.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface NativeHint {
}
