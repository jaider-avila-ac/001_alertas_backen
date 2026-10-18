package com.alertas.shared.validacion.validador;

import com.alertas.shared.validacion.Direccion;
import com.alertas.shared.validacion.Validaciones;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DireccionValidador implements ConstraintValidator<Direccion, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {

        if (valor == null || valor.isBlank()) {
            return true;
        }

        return Validaciones.esDireccion(valor);
    }
}
