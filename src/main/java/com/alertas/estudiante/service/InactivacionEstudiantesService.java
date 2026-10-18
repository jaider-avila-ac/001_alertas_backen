package com.alertas.estudiante.service;

import com.alertas.estudiante.dto.ResultadoInactivacionResponse;
import com.alertas.estudiante.dto.VistaPreviaInactivacionResponse;
import java.io.InputStream;

// inactivar estudiantes en bloque con un excel de documentos: vista previa y confirmar (todo o nada).
// es aparte de la carga: la carga solo crea y actualiza
public interface InactivacionEstudiantesService {

    byte[] plantilla();

    VistaPreviaInactivacionResponse vistaPrevia(InputStream archivo);

    ResultadoInactivacionResponse confirmar(String token);
}
