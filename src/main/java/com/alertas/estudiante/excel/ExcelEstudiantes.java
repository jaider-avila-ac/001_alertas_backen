package com.alertas.estudiante.excel;

import com.alertas.shared.excel.ArchivoExcel;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

// columnas e instrucciones del excel de estudiantes. leer y escribir lo hace ArchivoExcel
public final class ExcelEstudiantes {

    public static final String TIPO_DOC = "TIPO_DOC";
    public static final String NUMERO_DOCUMENTO = "NUMERO_DOCUMENTO";
    public static final String NOMBRES = "NOMBRES";
    public static final String APELLIDOS = "APELLIDOS";
    public static final String GENERO = "GENERO";
    public static final String FECHA_NACIMIENTO = "FECHA_NACIMIENTO";
    public static final String CELULAR = "CELULAR";
    public static final String GRUPO = "GRUPO";
    public static final String FAMILIAR_NOMBRE = "FAMILIAR_NOMBRE";
    public static final String FAMILIAR_PARENTESCO = "FAMILIAR_PARENTESCO";
    public static final String FAMILIAR_CELULAR = "FAMILIAR_CELULAR";
    public static final String CORREO = "CORREO";
    public static final String DIRECCION = "DIRECCION";
    public static final String BARRIO = "BARRIO";
    public static final String EPS = "EPS";
    public static final String RH = "RH";
    public static final String CONDICIONES_SALUD = "CONDICIONES_SALUD";

    public static final String[] COLUMNAS = {
            TIPO_DOC, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, GENERO, FECHA_NACIMIENTO, CELULAR,
            GRUPO, FAMILIAR_NOMBRE, FAMILIAR_PARENTESCO, FAMILIAR_CELULAR,
            CORREO, DIRECCION, BARRIO, EPS, RH, CONDICIONES_SALUD
    };

    private static final String[] OBLIGATORIAS = {NUMERO_DOCUMENTO};

    private static final String[][] INSTRUCCIONES = {
            {"Columna", "Obligatoria", "Que va"},
            {TIPO_DOC, "Si", "RC, TI, CC, CE o PPT"},
            {NUMERO_DOCUMENTO, "Si", "Solo letras y numeros, sin puntos. Sera el usuario y la contrasena inicial"},
            {NOMBRES, "Si", ""},
            {APELLIDOS, "Si", ""},
            {GENERO, "No", "F, M u O"},
            {FECHA_NACIMIENTO, "No", "dd/mm/aaaa, ej 25/03/2012"},
            {CELULAR, "No", "10 digitos, empieza por 3"},
            {GRUPO, "No", "Texto libre para ubicar al estudiante, ej: 6A o Ing. Sistemas - Semestre 3"},
            {FAMILIAR_NOMBRE, "No", "Nombre de un familiar o acudiente"},
            {FAMILIAR_PARENTESCO, "Si hay familiar", "MADRE, PADRE, ACUDIENTE, ABUELO, HERMANO, TIO u OTRO"},
            {FAMILIAR_CELULAR, "No", "10 digitos, empieza por 3. A este numero llegan los SMS"},
            {CORREO, "No", "Correo del estudiante"},
            {DIRECCION, "No", ""},
            {BARRIO, "No", ""},
            {EPS, "No", "Nombre de la EPS. Importante en una emergencia"},
            {RH, "No", "O+, O-, A+, A-, B+, B-, AB+ o AB-"},
            {CONDICIONES_SALUD, "No", "Alergias, enfermedades o medicamentos (maximo 500 letras)"},
            {"", "", ""},
            {"Ejemplo", "", "TI | 1067123456 | Ana Maria | Rios Perez | F | 25/03/2012 | | 6A | Luz Perez | MADRE | 3001234567"},
            {"Importante", "", "Si el estudiante ya existe (mismo documento) se actualizan sus datos. "
                    + "Una celda vacia no borra lo que ya tenia"},
            {"Cada año", "", "Sube solo a los estudiantes nuevos (o a los que cambian de datos). Para inactivar a los que ya no estan usa el Excel de inactivar"},
    };

    private ExcelEstudiantes() {
    }

    public static byte[] plantilla() {
        return ArchivoExcel.plantilla("Estudiantes", COLUMNAS, INSTRUCCIONES);
    }

    public static byte[] exportar(String[] columnas, List<String[]> filas) {
        return ArchivoExcel.exportar("Estudiantes", columnas, filas);
    }

    public static List<Map<String, String>> leer(InputStream archivo, int maximoFilas) {
        return ArchivoExcel.leer(archivo, COLUMNAS, OBLIGATORIAS, maximoFilas);
    }
}
