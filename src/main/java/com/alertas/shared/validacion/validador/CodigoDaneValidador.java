package com.alertas.shared.validacion.validador;

import com.alertas.shared.validacion.CodigoDane;
import com.alertas.shared.validacion.Validaciones;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class CodigoDaneValidador implements ConstraintValidator<CodigoDane, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {

        if (valor == null || valor.isBlank()) {
            return true;
        }

        return Validaciones.esCodigoDane(valor);
    }
}
