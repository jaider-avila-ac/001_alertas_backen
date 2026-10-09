package com.alertas.auth.model;

// lo que se saca del user-agent: tipo de equipo, sistema y navegador. sin ip ni nada que ubique a la persona
public record Navegador(String dispositivo, String sistema, String navegador) {

    public static Navegador leer(String userAgent) {

        if (userAgent == null || userAgent.isBlank()) {
            return new Navegador("Desconocido", "Desconocido", "Desconocido");
        }

        return new Navegador(dispositivo(userAgent), sistema(userAgent), navegador(userAgent));
    }

    private static String dispositivo(String ua) {

        boolean android = ua.contains("Android");

        if (ua.contains("iPad") || ua.contains("Tablet") || (android && !ua.contains("Mobile"))) {
            return "Tableta";
        }

        if (ua.contains("Mobi") || ua.contains("iPhone") || android) {
            return "Celular";
        }

        return "Computador";
    }

    private static String sistema(String ua) {

        if (ua.contains("Windows")) {
            return "Windows";
        }
        if (ua.contains("Android")) {
            return "Android";
        }
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod")) {
            return "iOS";
        }
        if (ua.contains("CrOS")) {
            return "ChromeOS";
        }
        if (ua.contains("Macintosh") || ua.contains("Mac OS X")) {
            return "macOS";
        }
        if (ua.contains("Linux")) {
            return "Linux";
        }

        return "Otro";
    }

    // el orden importa: edge, opera y samsung tambien dicen "Chrome" y casi todos dicen "Safari"
    private static String navegador(String ua) {

        if (ua.contains("Edg/") || ua.contains("EdgA/") || ua.contains("EdgiOS/")) {
            return "Edge";
        }
        if (ua.contains("OPR/") || ua.contains("Opera")) {
            return "Opera";
        }
        if (ua.contains("SamsungBrowser")) {
            return "Samsung Internet";
        }
        if (ua.contains("Firefox/") || ua.contains("FxiOS")) {
            return "Firefox";
        }
        if (ua.contains("Chrome/") || ua.contains("CriOS")) {
            return "Chrome";
        }
        if (ua.contains("Safari/")) {
            return "Safari";
        }

        return "Otro";
    }
}
