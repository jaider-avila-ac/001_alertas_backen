package com.alertas.inicio.dto;

import com.alertas.estadistica.dto.ConteoResponse;
import java.util.List;

// el dashboard de inicio en una sola respuesta. lo que no aplica para el rol viene vacio.
// estados: pendientes / en proceso / completadas. porMes (admin) y porNivel (docente) para los graficos.
// lista: lo proximo o lo ultimo segun el rol, con su titulo
public record InicioResponse(
        String rol,
        List<TarjetaResponse> tarjetas,
        List<ConteoResponse> estados,
        List<ConteoResponse> porMes,
        List<ConteoResponse> porNivel,
        String tituloLista,
        List<ElementoResponse> lista) {
}
