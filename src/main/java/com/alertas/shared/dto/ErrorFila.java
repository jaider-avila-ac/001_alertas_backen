package com.alertas.shared.dto;

// fila = numero de fila en el excel, como la ve el usuario
public record ErrorFila(int fila, String mensaje) {
}
