package com.alertas.estudiante.service;

import com.alertas.estudiante.dto.ResultadoImportacionResponse;
import com.alertas.estudiante.dto.VistaPreviaImportacionResponse;
import java.io.InputStream;

// carga de estudiantes por excel en dos pasos: vista previa (no guarda nada) y confirmar (todo o nada)
public interface ImportacionEstudiantesService {

    byte[] plantilla();

    VistaPreviaImportacionResponse vistaPrevia(InputStream archivo);

    ResultadoImportacionResponse confirmar(String token);

    // mismos filtros del listado
    byte[] exportar(String texto, String edad, Boolean activo);
}
