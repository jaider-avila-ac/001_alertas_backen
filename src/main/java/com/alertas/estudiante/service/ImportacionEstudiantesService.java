package com.alertas.estudiante.service;

import com.alertas.estudiante.dto.ResultadoImportacionResponse;
import com.alertas.estudiante.dto.VistaPreviaImportacionResponse;
import java.io.InputStream;

// carga de estudiantes por excel en dos pasos: vista previa (no guarda nada) y confirmar (todo o nada)
public interface ImportacionEstudiantesService {

    byte[] plantilla();

    // anioId null = el anio activo. para promover al anio siguiente se manda ese anio
    VistaPreviaImportacionResponse vistaPrevia(InputStream archivo, Long anioId);

    ResultadoImportacionResponse confirmar(String token);

    // mismos filtros del listado
    byte[] exportar(String texto, Long gradoId, Long grupoId, Boolean activo);
}
