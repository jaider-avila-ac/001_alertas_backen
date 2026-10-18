package com.alertas.shared.validacion;

import com.alertas.shared.validacion.validador.ConLetrasValidador;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// textos libres: que no sean solo numeros o signos. vacio pasa: para obligarlo se usa @NotBlank
@Documented
@Constraint(validatedBy = ConLetrasValidador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ConLetras {

    String message() default "Escribe un texto con letras";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
