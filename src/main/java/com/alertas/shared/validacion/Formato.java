package com.alertas.shared.validacion;

import java.util.Locale;
import java.util.Set;

// deja los datos como se guardan: sin espacios de mas, nombres con mayuscula inicial, correo en minuscula.
// todos devuelven null si el texto viene vacio
public final class Formato {

    private static final Locale ESPANOL = Locale.forLanguageTag("es-CO");

    // van en minuscula si no son la primera palabra: "Maria de los Angeles"
    private static final Set<String> PARTICULAS = Set.of("de", "del", "la", "las", "los", "y", "e");

    private Formato() {
    }

    public static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    // quita los espacios de los lados y deja uno solo entre palabras
    public static String espacios(String texto) {

        if (vacio(texto)) {
            return null;
        }

        return texto.trim().replaceAll("\\s+", " ");
    }

    // textos largos (descripciones, observaciones): se respetan los saltos de linea
    public static String texto(String texto) {

        if (vacio(texto)) {
            return null;
        }

        return texto.trim();
    }

    // "mARIA   jOSE de la CRUZ" -> "Maria Jose de la Cruz"
    public static String nombrePropio(String texto) {

        String limpio = espacios(texto);

        if (limpio == null) {
            return null;
        }

        limpio = limpio.replace('’', '\'').toLowerCase(ESPANOL);
        String[] palabras = limpio.split(" ");
        StringBuilder resultado = new StringBuilder();

        for (int i = 0; i < palabras.length; i++) {
            String palabra = palabras[i];

            if (i > 0) {
                resultado.append(' ');
            }

            if (i > 0 && PARTICULAS.contains(palabra)) {
                resultado.append(palabra);
            } else {
                resultado.append(mayusculaInicial(palabra));
            }
        }

        return resultado.toString();
    }

    public static String correo(String texto) {

        if (vacio(texto)) {
            return null;
        }

        return texto.trim().toLowerCase(ESPANOL);
    }

    // quita espacios, guiones, puntos y parentesis. tambien el +57 del pais
    public static String telefono(String texto) {

        if (vacio(texto)) {
            return null;
        }

        String limpio = texto.trim();

        if (limpio.startsWith("+57")) {
            limpio = limpio.substring(3);
        }

        limpio = limpio.replaceAll("[\\s\\-.()]", "");

        if (limpio.length() == 12 && limpio.startsWith("57")) {
            limpio = limpio.substring(2);
        }

        return limpio;
    }

    // el documento se escribe a veces con puntos o espacios: 1.067.000.111
    public static String documento(String texto) {

        if (vacio(texto)) {
            return null;
        }

        return texto.trim().replaceAll("[\\s.\\-]", "");
    }

    // la primera letra en mayuscula y las demas en minuscula. tambien despues de - ' y .
    private static String mayusculaInicial(String palabra) {

        StringBuilder resultado = new StringBuilder();
        boolean siguienteMayuscula = true;

        for (int i = 0; i < palabra.length(); i++) {
            char letra = palabra.charAt(i);

            if (siguienteMayuscula && Character.isLetter(letra)) {
                resultado.append(Character.toUpperCase(letra));
            } else {
                resultado.append(letra);
            }

            if (Character.isLetterOrDigit(letra)) {
                siguienteMayuscula = false;
            }
            if (letra == '-' || letra == '\'' || letra == '.') {
                siguienteMayuscula = true;
            }
        }

        return resultado.toString();
    }
}
