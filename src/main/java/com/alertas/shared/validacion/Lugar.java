package com.alertas.shared.validacion;

import com.alertas.shared.validacion.validador.LugarValidador;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// municipio o departamento. vacio pasa: para obligarlo se usa @NotBlank
@Documented
@Constraint(validatedBy = LugarValidador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Lugar {

    String message() default "Solo se permiten letras";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
