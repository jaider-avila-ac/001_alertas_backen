package com.alertas.matricula.dto;

// sinDestino: estudiantes de grupos que quedaron sin grupo destino
public record ResultadoPromocionResponse(int promovidos, int graduados, int sinDestino) {
}
