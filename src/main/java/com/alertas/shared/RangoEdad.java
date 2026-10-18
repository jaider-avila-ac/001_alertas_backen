package com.alertas.shared;

// los estudiantes se agrupan por rango de edad (sirve igual en colegios, universidades e instituciones tecnicas).
// las mismas claves en el listado de estudiantes, en los filtros y en las estadisticas
public final class RangoEdad {

    public static final String SIN_FECHA = "SIN_FECHA";

    // {clave, texto para mostrar}
    public static final String[][] RANGOS = {
        {"HASTA_8", "8 o menos"},
        {"DE_9_A_11", "9 a 11"},
        {"DE_12_A_14", "12 a 14"},
        {"DE_15_A_17", "15 a 17"},
        {"DE_18_A_24", "18 a 24"},
        {"DESDE_25", "25 o mas"}};

    // la clave del rango en sql para la edad de hoy. va dentro de las consultas de los repositorios
    public static final String SQL_HOY = """
            CASE
                WHEN e.est_fecha_nacimiento IS NULL THEN 'SIN_FECHA'
                WHEN extract(year FROM age(e.est_fecha_nacimiento)) <= 8 THEN 'HASTA_8'
                WHEN extract(year FROM age(e.est_fecha_nacimiento)) <= 11 THEN 'DE_9_A_11'
                WHEN extract(year FROM age(e.est_fecha_nacimiento)) <= 14 THEN 'DE_12_A_14'
                WHEN extract(year FROM age(e.est_fecha_nacimiento)) <= 17 THEN 'DE_15_A_17'
                WHEN extract(year FROM age(e.est_fecha_nacimiento)) <= 24 THEN 'DE_18_A_24'
                ELSE 'DESDE_25'
            END""";

    private RangoEdad() {
    }

    // la misma clave en sql pero con la edad que tenia en otra fecha (ej. el dia de la alerta)
    public static String sqlEnFecha(String nacimiento, String fecha) {

        String edad = "extract(year FROM age(" + fecha + ", " + nacimiento + "))";

        return "CASE"
                + " WHEN " + nacimiento + " IS NULL THEN 'SIN_FECHA'"
                + " WHEN " + edad + " <= 8 THEN 'HASTA_8'"
                + " WHEN " + edad + " <= 11 THEN 'DE_9_A_11'"
                + " WHEN " + edad + " <= 14 THEN 'DE_12_A_14'"
                + " WHEN " + edad + " <= 17 THEN 'DE_15_A_17'"
                + " WHEN " + edad + " <= 24 THEN 'DE_18_A_24'"
                + " ELSE 'DESDE_25' END";
    }

    // null si la clave no es de un rango (ni SIN_FECHA)
    public static String validar(String clave) {

        if (clave == null || clave.isBlank()) {
            return null;
        }

        if (SIN_FECHA.equals(clave)) {
            return clave;
        }

        for (String[] rango : RANGOS) {
            if (rango[0].equals(clave)) {
                return clave;
            }
        }

        return null;
    }
}
