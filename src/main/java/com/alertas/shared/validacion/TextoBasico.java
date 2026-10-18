package com.alertas.shared.validacion;

import com.alertas.shared.validacion.validador.TextoBasicoValidador;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// barrio, eps, nombre del colegio o de una categoria: letras, numeros y signos comunes. vacio pasa: para obligarlo se usa @NotBlank
@Documented
@Constraint(validatedBy = TextoBasicoValidador.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface TextoBasico {

    String message() default "Tiene caracteres que no se permiten";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
