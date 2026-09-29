package com.alertas.estadistica.dto;

// totales del superadmin. segmentos: partes de 160 caracteres, que es lo que cobra el proveedor
public record ResumenGlobalResponse(
        long institucionesActivas,
        long institucionesInactivas,
        long alertas,
        long smsEnviados,
        long smsFallidos,
        long smsSegmentos) {
}
