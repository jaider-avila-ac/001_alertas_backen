package com.alertas.estadistica.dto;

// alertas que tiene o tuvo a cargo y cuantas de ellas completo
public record PsicorientadorConteoResponse(String nombre, long atendidas, long completadas) {
}
