package com.alertas.shared.validacion;

import com.alertas.shared.validacion.validador.DocumentoValidoValidador;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// va sobre el record (que implementa ConDocumento) porque la regla depende del tipo de documento
@Documented
@Constraint(validatedBy = DocumentoValidoValidador.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface DocumentoValido {

    String message() default "El numero de documento no es valido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
