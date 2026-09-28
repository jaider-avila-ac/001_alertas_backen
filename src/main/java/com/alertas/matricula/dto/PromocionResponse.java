package com.alertas.matricula.dto;

import com.alertas.estructura.dto.GrupoResponse;
import java.util.List;

// del anio activo (origen) al siguiente (destino)
public record PromocionResponse(
        Long origenAnioId,
        int origenAnio,
        Long destinoAnioId,
        int destinoAnio,
        List<GrupoPorCrear> gruposPorCrear,
        List<GrupoPromocion> grupos,
        List<GrupoResponse> gruposDestino) {
}
