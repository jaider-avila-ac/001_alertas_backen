package com.alertas.shared.validacion;

import static org.assertj.core.api.Assertions.assertThat;

import com.alertas.estudiante.dto.EstudianteRequest;
import com.alertas.institucion.dto.InstitucionDatosRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

// las anotaciones sobre los dto, sin levantar spring
class AnotacionesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private EstudianteRequest estudiante(String tipoDoc, String nroDoc, String nombres, String celular, String correo) {
        return new EstudianteRequest(tipoDoc, nroDoc, nombres, "Rios", null, null, celular, correo,
                null, null, null, null, null, "6A");
    }

    private List<String> mensajes(Object objeto) {

        Set<ConstraintViolation<Object>> errores = validator.validate(objeto);
        List<String> mensajes = new ArrayList<>();

        for (ConstraintViolation<Object> error : errores) {
            mensajes.add(error.getPropertyPath() + ": " + error.getMessage());
        }

        return mensajes;
    }

    @Test
    void estudianteBienNoTieneErrores() {
        assertThat(mensajes(estudiante("TI", "1100001", "Sofia", "3001234567", "sofia@gmail.com"))).isEmpty();
    }

    @Test
    void documentoConLetrasSoloEnCe() {

        assertThat(mensajes(estudiante("TI", "11000A1", "Sofia", null, null)))
                .containsExactly("nroDoc: El numero de documento solo lleva numeros, de 3 a 15 digitos");

        assertThat(mensajes(estudiante("CE", "AB12345", "Sofia", null, null))).isEmpty();
    }

    @Test
    void nombreCelularYCorreoMalos() {

        assertThat(mensajes(estudiante("TI", "1100001", "Sofia2", "300123", "sofia@gmail")))
                .containsExactlyInAnyOrder(
                        "nombres: Los nombres solo llevan letras",
                        "celular: El celular debe tener 10 digitos y empezar por 3",
                        "correo: El correo no es valido");
    }

    @Test
    void institucion() {

        InstitucionDatosRequest bien = new InstitucionDatosRequest("I.E. San José", "san-jose", "123001000123",
                "Montería", "Córdoba", "Calle 10 # 5-20", "6047891234", "colegio@sanjose.edu.co");
        assertThat(mensajes(bien)).isEmpty();

        InstitucionDatosRequest mal = new InstitucionDatosRequest("I.E. San José", "san-jose", "123",
                "Monteria 2", "Córdoba", "Calle 10 # 5-20", "604-ABC", "colegio@sanjose");
        assertThat(mensajes(mal)).containsExactlyInAnyOrder(
                "codigoDane: El codigo DANE tiene 12 numeros",
                "municipio: El municipio solo lleva letras",
                "telefono: El telefono solo lleva numeros, de 7 a 10 digitos",
                "correo: El correo no es valido");
    }
}
