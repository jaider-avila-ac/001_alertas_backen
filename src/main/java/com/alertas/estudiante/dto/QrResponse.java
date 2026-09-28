package com.alertas.estudiante.dto;

// el enlace que va dentro del qr. no lleva ningun dato personal, solo el codigo del qr
public record QrResponse(String nombres, String apellidos, String gradoNombre, String grupoNombre, String enlace) {
}
