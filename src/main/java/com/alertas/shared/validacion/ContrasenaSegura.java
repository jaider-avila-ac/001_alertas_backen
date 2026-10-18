package com.alertas.shared.validacion;

import com.alertas.shared.validacion.validador.ContrasenaSeguraValidador;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// contrasena con al menos una letra y un numero. vacio pasa: para obligarlo se usa @NotBlank
@Documented
@Constraint(validatedBy = ContrasenaSeguraValidador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ContrasenaSegura {

    String message() default "La contrasena debe tener al menos una letra y un numero";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
