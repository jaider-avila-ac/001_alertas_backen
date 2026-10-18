package com.alertas.estudiante.service;

import java.time.LocalDate;
import java.time.Period;

// regla de la fecha de nacimiento. la plataforma sirve para colegios y para universidades,
// asi que no hay edad maxima: solo que no sea futura, que tenga al menos 3 anios y no antes de 1950
public final class FechaNacimiento {

    public static final int EDAD_MINIMA = 3;
    public static final LocalDate MAS_ANTIGUA = LocalDate.of(1950, 1, 1);

    private FechaNacimiento() {
    }

    // null si esta bien (o no viene); si no, el problema para mostrarle al usuario
    public static String problema(LocalDate fecha, LocalDate hoy) {

        if (fecha == null) {
            return null;
        }

        if (fecha.isAfter(hoy)) {
            return "La fecha de nacimiento no puede ser una fecha futura";
        }

        if (fecha.isBefore(MAS_ANTIGUA)) {
            return "La fecha de nacimiento no es valida: debe ser desde 1950";
        }

        int edad = Period.between(fecha, hoy).getYears();
        if (edad < EDAD_MINIMA) {
            return "La fecha de nacimiento no es valida: el estudiante tendria " + edad + " años (minimo " + EDAD_MINIMA + ")";
        }

        return null;
    }
}
