package com.alertas.sesion.dto;

import com.alertas.shared.dto.PageResponse;

// resumen de la institucion (sin filtro) y la pagina pedida
public record SesionesResponse(ResumenSesionesResponse resumen, PageResponse<SesionActivaResponse> pagina) {
}
