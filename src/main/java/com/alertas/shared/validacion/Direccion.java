package com.alertas.shared.validacion;

import com.alertas.shared.validacion.validador.DireccionValidador;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// direccion: letras, numeros y los signos de siempre (# - . ,). vacio pasa: para obligarlo se usa @NotBlank
@Documented
@Constraint(validatedBy = DireccionValidador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Direccion {

    String message() default "La direccion no es valida";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
