package com.alertas.estudiante.excel;

import com.alertas.shared.excel.ArchivoExcel;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

// excel para inactivar estudiantes en bloque. el programa solo lee el documento; el nombre es para quien lo llena
public final class ExcelInactivacion {

    public static final String NUMERO_DOCUMENTO = "NUMERO_DOCUMENTO";
    public static final String NOMBRE = "NOMBRE";

    public static final String[] COLUMNAS = {NUMERO_DOCUMENTO, NOMBRE};

    private static final String[] OBLIGATORIAS = {NUMERO_DOCUMENTO};

    private static final String[][] INSTRUCCIONES = {
            {"Columna", "Obligatoria", "Que va"},
            {NUMERO_DOCUMENTO, "Si", "Documento del estudiante que ya no esta en la institucion, sin puntos"},
            {NOMBRE, "No", "Nombre del estudiante, solo como referencia. El programa no lo lee"},
            {"", "", ""},
            {"Ejemplo", "", "1067123456 | Ana Maria Rios Perez"},
            {"Importante", "", "No se borra nada: el estudiante queda inactivo, conserva su historial y se puede volver a activar"},
    };

    private ExcelInactivacion() {
    }

    public static byte[] plantilla() {
        return ArchivoExcel.plantilla("Inactivar", COLUMNAS, INSTRUCCIONES);
    }

    public static List<Map<String, String>> leer(InputStream archivo, int maximoFilas) {
        return ArchivoExcel.leer(archivo, COLUMNAS, OBLIGATORIAS, maximoFilas);
    }
}
