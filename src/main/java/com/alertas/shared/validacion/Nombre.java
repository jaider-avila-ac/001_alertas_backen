package com.alertas.shared.validacion;

import com.alertas.shared.validacion.validador.NombreValidador;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// nombres y apellidos de personas: solo letras, espacios, guion y apostrofe. vacio pasa: para obligarlo se usa @NotBlank
@Documented
@Constraint(validatedBy = NombreValidador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Nombre {

    String message() default "Solo se permiten letras";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
