package com.alertas.estadistica.dto;

public record SmsMesResponse(String clave, String etiqueta, long enviados, long fallidos, long segmentos) {
}
