package com.alertas.shared.validacion;

import com.alertas.shared.validacion.validador.CelularValidador;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// celular colombiano de 10 digitos que empieza por 3. vacio pasa: para obligarlo se usa @NotBlank
@Documented
@Constraint(validatedBy = CelularValidador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Celular {

    String message() default "El celular debe tener 10 digitos y empezar por 3";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
