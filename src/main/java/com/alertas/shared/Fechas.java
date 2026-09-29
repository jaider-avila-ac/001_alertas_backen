package com.alertas.shared;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// fechas en texto para mensajes (notificaciones y sms), en la hora del servidor
public final class Fechas {

    private static final DateTimeFormatter CITA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", Locale.forLanguageTag("es-CO"));

    private Fechas() {
    }

    // "lunes 30 de septiembre a las 10:00"
    public static String cita(OffsetDateTime fecha) {
        return fecha.atZoneSameInstant(ZoneId.systemDefault()).format(CITA);
    }
}
