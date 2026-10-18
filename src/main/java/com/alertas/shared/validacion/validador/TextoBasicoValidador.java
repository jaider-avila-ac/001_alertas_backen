package com.alertas.shared.validacion.validador;

import com.alertas.shared.validacion.TextoBasico;
import com.alertas.shared.validacion.Validaciones;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TextoBasicoValidador implements ConstraintValidator<TextoBasico, String> {

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext context) {

        if (valor == null || valor.isBlank()) {
            return true;
        }

        return Validaciones.esTextoBasico(valor);
    }
}
