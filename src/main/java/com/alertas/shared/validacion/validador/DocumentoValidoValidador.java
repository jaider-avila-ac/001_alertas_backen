package com.alertas.shared.validacion.validador;

import com.alertas.shared.validacion.ConDocumento;
import com.alertas.shared.validacion.DocumentoValido;
import com.alertas.shared.validacion.Validaciones;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DocumentoValidoValidador implements ConstraintValidator<DocumentoValido, ConDocumento> {

    @Override
    public boolean isValid(ConDocumento datos, ConstraintValidatorContext context) {

        // si falta alguno lo avisa el @NotBlank del campo
        if (datos == null || datos.tipoDoc() == null || datos.nroDoc() == null || datos.nroDoc().isBlank()) {
            return true;
        }

        String problema = Validaciones.problemaDocumento(datos.tipoDoc(), datos.nroDoc());

        if (problema == null) {
            return true;
        }

        // el mensaje cambia segun el tipo de documento
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(problema)
                .addPropertyNode("nroDoc")
                .addConstraintViolation();
        return false;
    }
}
