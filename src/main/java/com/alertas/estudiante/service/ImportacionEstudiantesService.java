package com.alertas.estudiante.service;

import com.alertas.estudiante.dto.ResultadoImportacionResponse;
import com.alertas.estudiante.dto.VistaPreviaImportacionResponse;
import java.io.InputStream;

// carga de estudiantes por excel en dos pasos: vista previa (no guarda nada) y confirmar (todo o nada)
public interface ImportacionEstudiantesService {

    byte[] plantilla();

    // inactivarAusentes: al confirmar, los activos que no vienen en el archivo quedan inactivos
    VistaPreviaImportacionResponse vistaPrevia(InputStream archivo, boolean inactivarAusentes);

    ResultadoImportacionResponse confirmar(String token);

    // mismos filtros del listado
    byte[] exportar(String texto, String grupo, String edad, Boolean activo);
}
