package com.alertas.estudiante.service;

import java.time.LocalDate;
import java.time.Period;

// regla de la fecha de nacimiento: una edad posible para un estudiante (de transicion a extraedad)
public final class FechaNacimiento {

    public static final int EDAD_MINIMA = 3;
    public static final int EDAD_MAXIMA = 25;

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

        int edad = Period.between(fecha, hoy).getYears();

        if (edad < EDAD_MINIMA || edad > EDAD_MAXIMA) {
            return "La fecha de nacimiento no es valida: el estudiante tendria " + edad + " años (debe tener entre "
                    + EDAD_MINIMA + " y " + EDAD_MAXIMA + ")";
        }

        return null;
    }
}
