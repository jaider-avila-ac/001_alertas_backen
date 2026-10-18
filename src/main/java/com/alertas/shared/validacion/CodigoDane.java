package com.alertas.shared.validacion;

import com.alertas.shared.validacion.validador.CodigoDaneValidador;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// codigo DANE del establecimiento: 12 numeros. vacio pasa: para obligarlo se usa @NotBlank
@Documented
@Constraint(validatedBy = CodigoDaneValidador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CodigoDane {

    String message() default "El codigo DANE tiene 12 numeros";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
