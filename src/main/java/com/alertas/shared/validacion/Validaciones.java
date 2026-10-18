package com.alertas.shared.validacion;

import java.util.regex.Pattern;

// reglas de los datos que se escriben a mano. las usan las anotaciones de los dto y las importaciones de excel.
// cada metodo recibe el texto como llega y lo limpia con Formato antes de revisarlo
public final class Validaciones {

    // letras (con tildes y ñ) separadas por un espacio, guion o apostrofe: "Maria Jose", "Perez-Gomez", "D'Angelo"
    private static final Pattern NOMBRE = Pattern.compile("\\p{L}+([ '’\\-]\\p{L}+)*");

    // municipios y departamentos: "Bogota D.C.", "San Andres, Providencia y Santa Catalina"
    private static final Pattern LUGAR = Pattern.compile("[\\p{L} .,'’\\-]+");

    // barrio, eps, nombre del colegio, categorias
    private static final Pattern TEXTO_BASICO = Pattern.compile("[\\p{L}0-9 .,'’\"\\-()&/#°º]+");

    private static final Pattern DIRECCION = Pattern.compile("[\\p{L}0-9 .,'’\\-()#°º/]+");

    private static final Pattern CELULAR = Pattern.compile("3[0-9]{9}");

    // fijo de 7 digitos (viejo), fijo nuevo de 10 (601...) o celular
    private static final Pattern TELEFONO = Pattern.compile("[0-9]{7,10}");

    // sin puntos seguidos ni al inicio o al final, y el dominio con su terminacion (.com, .edu.co)
    private static final Pattern CORREO = Pattern.compile(
            "[a-z0-9_%+\\-]+(\\.[a-z0-9_%+\\-]+)*@([a-z0-9]([a-z0-9\\-]*[a-z0-9])?\\.)+[a-z]{2,24}");

    private static final Pattern DOCUMENTO_NUMERICO = Pattern.compile("[0-9]{3,15}");
    private static final Pattern DOCUMENTO_EXTRANJERO = Pattern.compile("[A-Za-z0-9]{3,20}");

    private static final Pattern CODIGO_DANE = Pattern.compile("[0-9]{12}");

    private static final Pattern LETRA = Pattern.compile("\\p{L}");
    private static final Pattern NUMERO = Pattern.compile("[0-9]");

    private Validaciones() {
    }

    public static boolean esNombre(String texto) {

        String limpio = Formato.espacios(texto);

        if (limpio == null || limpio.length() < 2) {
            return false;
        }

        return NOMBRE.matcher(limpio).matches();
    }

    public static boolean esLugar(String texto) {

        String limpio = Formato.espacios(texto);

        if (limpio == null || limpio.length() < 2) {
            return false;
        }

        return LUGAR.matcher(limpio).matches() && tieneLetra(limpio);
    }

    public static boolean esTextoBasico(String texto) {

        String limpio = Formato.espacios(texto);

        if (limpio == null) {
            return false;
        }

        return TEXTO_BASICO.matcher(limpio).matches() && tieneLetra(limpio);
    }

    public static boolean esDireccion(String texto) {

        String limpio = Formato.espacios(texto);

        if (limpio == null || limpio.length() < 3) {
            return false;
        }

        return DIRECCION.matcher(limpio).matches() && tieneLetra(limpio);
    }

    public static boolean esCelular(String texto) {

        String limpio = Formato.telefono(texto);

        if (limpio == null) {
            return false;
        }

        return CELULAR.matcher(limpio).matches();
    }

    public static boolean esTelefono(String texto) {

        String limpio = Formato.telefono(texto);

        if (limpio == null) {
            return false;
        }

        return TELEFONO.matcher(limpio).matches();
    }

    public static boolean esCorreo(String texto) {

        String limpio = Formato.correo(texto);

        if (limpio == null || limpio.length() > 120) {
            return false;
        }

        // la parte antes de la @ no pasa de 64
        int arroba = limpio.indexOf('@');
        if (arroba > 64) {
            return false;
        }

        return CORREO.matcher(limpio).matches();
    }

    public static boolean esCodigoDane(String texto) {

        if (Formato.vacio(texto)) {
            return false;
        }

        return CODIGO_DANE.matcher(texto.trim()).matches();
    }

    // al menos una letra y un numero
    public static boolean esContrasenaSegura(String texto) {

        if (texto == null) {
            return false;
        }

        return tieneLetra(texto) && NUMERO.matcher(texto).find();
    }

    public static boolean tieneLetra(String texto) {

        if (texto == null) {
            return false;
        }

        return LETRA.matcher(texto).find();
    }

    // null si esta bien; si no, el problema para mostrar. CE puede llevar letras, los demas solo numeros
    public static String problemaDocumento(String tipoDoc, String nroDoc) {

        String limpio = Formato.documento(nroDoc);

        if (limpio == null) {
            return "El numero de documento es obligatorio";
        }

        if ("CE".equals(tipoDoc)) {
            if (!DOCUMENTO_EXTRANJERO.matcher(limpio).matches()) {
                return "La cedula de extranjeria solo lleva letras y numeros, de 3 a 20";
            }
            return null;
        }

        if (!DOCUMENTO_NUMERICO.matcher(limpio).matches()) {
            return "El numero de documento solo lleva numeros, de 3 a 15 digitos";
        }

        if (limpio.replace("0", "").isEmpty()) {
            return "El numero de documento no es valido";
        }

        return null;
    }
}
