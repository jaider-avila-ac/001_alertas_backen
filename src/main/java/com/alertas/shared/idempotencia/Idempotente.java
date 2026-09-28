package com.alertas.shared.idempotencia;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// en endpoints donde un doble clic crearia dos cosas (alertas, citas, importaciones).
// el front manda la cabecera Idempotency-Key con un uuid por cada envio
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotente {
}
