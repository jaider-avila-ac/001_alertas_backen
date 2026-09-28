package com.alertas.personal.excel;

import com.alertas.shared.excel.ArchivoExcel;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

// columnas e instrucciones del excel de docentes y psicorientadores
public final class ExcelPersonal {

    public static final String TIPO_DOC = "TIPO_DOC";
    public static final String NUMERO_DOCUMENTO = "NUMERO_DOCUMENTO";
    public static final String NOMBRES = "NOMBRES";
    public static final String APELLIDOS = "APELLIDOS";
    public static final String ROL = "ROL";
    public static final String CORREO = "CORREO";
    public static final String CELULAR = "CELULAR";

    public static final String[] COLUMNAS = {TIPO_DOC, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, ROL, CORREO, CELULAR};

    private static final String[] OBLIGATORIAS = {NUMERO_DOCUMENTO, ROL};

    private static final String[][] INSTRUCCIONES = {
            {"Columna", "Obligatoria", "Que va"},
            {TIPO_DOC, "Si", "CC, CE, PPT, TI o RC"},
            {NUMERO_DOCUMENTO, "Si", "Solo letras y numeros, sin puntos. Sera el usuario y la contrasena inicial"},
            {NOMBRES, "Si", ""},
            {APELLIDOS, "Si", ""},
            {ROL, "Si", "DOCENTE o PSICORIENTADOR"},
            {CORREO, "No", ""},
            {CELULAR, "No", "10 digitos, empieza por 3"},
            {"", "", ""},
            {"Ejemplo", "", "CC | 1067123456 | Carlos | Mendoza | DOCENTE | carlos@correo.com | 3001234567"},
            {"Importante", "", "Si la persona ya existe (mismo documento) se actualizan sus datos. El rol no cambia"},
    };

    private ExcelPersonal() {
    }

    public static byte[] plantilla() {
        return ArchivoExcel.plantilla("Personal", COLUMNAS, INSTRUCCIONES);
    }

    public static byte[] exportar(String[] columnas, List<String[]> filas) {
        return ArchivoExcel.exportar("Personal", columnas, filas);
    }

    public static List<Map<String, String>> leer(InputStream archivo, int maximoFilas) {
        return ArchivoExcel.leer(archivo, COLUMNAS, OBLIGATORIAS, maximoFilas);
    }
}
