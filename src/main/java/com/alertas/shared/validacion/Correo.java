package com.alertas.shared.validacion;

import com.alertas.shared.validacion.validador.CorreoValidador;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// correo con usuario, @ y dominio con su terminacion. vacio pasa: para obligarlo se usa @NotBlank
@Documented
@Constraint(validatedBy = CorreoValidador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Correo {

    String message() default "El correo no es valido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
