package com.alertas.inicio.dto;

// una tarjeta de arriba: clave (para el color y el icono en el front), numero o texto, y a donde lleva
public record TarjetaResponse(String clave, String titulo, String valor, String detalle, String enlace) {
}
