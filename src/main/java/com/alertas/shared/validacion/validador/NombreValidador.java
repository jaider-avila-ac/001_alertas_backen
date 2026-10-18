package com.alertas.shared.validacion.validador;

import com.alertas.shared.validacion.Nombre;
import com.alertas.shared.validacion.Validaciones;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NombreValidador implements ConstraintValidator<Nombre, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {

        if (valor == null || valor.isBlank()) {
            return true;
        }

        return Validaciones.esNombre(valor);
    }
}
