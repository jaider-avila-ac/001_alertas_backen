package com.alertas.shared.validacion;

import com.alertas.shared.validacion.validador.TelefonoValidador;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// telefono fijo o celular, solo numeros. vacio pasa: para obligarlo se usa @NotBlank
@Documented
@Constraint(validatedBy = TelefonoValidador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface Telefono {

    String message() default "El telefono solo lleva numeros, de 7 a 10 digitos";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
