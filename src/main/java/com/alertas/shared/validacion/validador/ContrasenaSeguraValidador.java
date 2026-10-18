package com.alertas.shared.validacion.validador;

import com.alertas.shared.validacion.ContrasenaSegura;
import com.alertas.shared.validacion.Validaciones;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ContrasenaSeguraValidador implements ConstraintValidator<ContrasenaSegura, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {

        if (valor == null || valor.isBlank()) {
            return true;
        }

        return Validaciones.esContrasenaSegura(valor);
    }
}
