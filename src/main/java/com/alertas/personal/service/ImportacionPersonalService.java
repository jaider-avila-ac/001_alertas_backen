package com.alertas.personal.service;

import com.alertas.personal.dto.ResultadoImportacionPersonalResponse;
import com.alertas.personal.dto.VistaPreviaPersonalResponse;
import java.io.InputStream;

// carga de docentes y psicorientadores por excel: vista previa (no guarda nada) y confirmar (todo o nada)
public interface ImportacionPersonalService {

    byte[] plantilla();

    VistaPreviaPersonalResponse vistaPrevia(InputStream archivo);

    ResultadoImportacionPersonalResponse confirmar(String token);

    // mismos filtros del listado
    byte[] exportar(String texto, String rol, Boolean activo);
}
