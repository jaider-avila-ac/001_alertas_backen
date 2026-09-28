package com.alertas.estudiante.dto;

import com.alertas.matricula.dto.MatriculaResponse;
import java.time.LocalDate;
import java.util.List;

// matriculaActual es null si no tiene matricula en el anio activo.
// trayectoria: todas sus matriculas, de la mas reciente a la mas vieja
public record EstudianteDetalleResponse(
        String codigo,
        String tipoDoc,
        String nroDoc,
        String nombres,
        String apellidos,
        String genero,
        LocalDate fechaNacimiento,
        String celular,
        String correo,
        String direccion,
        String barrio,
        String eps,
        String rh,
        String condicionesSalud,
        boolean smsFamiliares,
        boolean activo,
        boolean debeCambiarContrasena,
        MatriculaResponse matriculaActual,
        List<MatriculaResponse> trayectoria,
        List<FamiliarResponse> familiares) {
}
