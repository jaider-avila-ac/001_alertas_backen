package com.alertas.shared;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// fechas en texto para mensajes (notificaciones y sms) y graficos, en la hora del servidor
public final class Fechas {

    private static final DateTimeFormatter CITA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'a las' HH:mm", Locale.forLanguageTag("es-CO"));

    private Fechas() {
    }

    private static final String[] MESES = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};

    // "lunes 30 de septiembre a las 10:00"
    public static String cita(OffsetDateTime fecha) {
        return fecha.atZoneSameInstant(ZoneId.systemDefault()).format(CITA);
    }

    // "mar 2026", para los graficos por mes
    public static String mesCorto(YearMonth mes) {
        return MESES[mes.getMonthValue() - 1] + " " + mes.getYear();
    }
}
