package com.alertas.estudiante.excel;

import com.alertas.shared.exception.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

// lee y escribe el excel de estudiantes. aqui no hay reglas de negocio, solo celdas
public final class ExcelEstudiantes {

    public static final String TIPO_DOC = "TIPO_DOC";
    public static final String NUMERO_DOCUMENTO = "NUMERO_DOCUMENTO";
    public static final String NOMBRES = "NOMBRES";
    public static final String APELLIDOS = "APELLIDOS";
    public static final String GENERO = "GENERO";
    public static final String FECHA_NACIMIENTO = "FECHA_NACIMIENTO";
    public static final String CELULAR = "CELULAR";
    public static final String GRADO = "GRADO";
    public static final String GRUPO = "GRUPO";
    public static final String FAMILIAR_NOMBRE = "FAMILIAR_NOMBRE";
    public static final String FAMILIAR_PARENTESCO = "FAMILIAR_PARENTESCO";
    public static final String FAMILIAR_CELULAR = "FAMILIAR_CELULAR";

    public static final String[] COLUMNAS = {
            TIPO_DOC, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, GENERO, FECHA_NACIMIENTO, CELULAR,
            GRADO, GRUPO, FAMILIAR_NOMBRE, FAMILIAR_PARENTESCO, FAMILIAR_CELULAR
    };

    private static final String[][] INSTRUCCIONES = {
            {"Columna", "Obligatoria", "Que va"},
            {TIPO_DOC, "Si", "RC, TI, CC, CE o PPT"},
            {NUMERO_DOCUMENTO, "Si", "Solo letras y numeros, sin puntos. Sera el usuario y la contrasena inicial"},
            {NOMBRES, "Si", ""},
            {APELLIDOS, "Si", ""},
            {GENERO, "No", "F, M u O"},
            {FECHA_NACIMIENTO, "No", "dd/mm/aaaa, ej 25/03/2012"},
            {CELULAR, "No", "10 digitos, empieza por 3"},
            {GRADO, "Si", "Numero o nombre: 6, 6°, Sexto. Transicion = 0"},
            {GRUPO, "Si", "Como se llama el grupo: A, B, 01... Si no existe en el anio se crea"},
            {FAMILIAR_NOMBRE, "No", "Nombre de un familiar o acudiente"},
            {FAMILIAR_PARENTESCO, "Si hay familiar", "MADRE, PADRE, ACUDIENTE, ABUELO, HERMANO, TIO u OTRO"},
            {FAMILIAR_CELULAR, "No", "10 digitos, empieza por 3. A este numero llegan los SMS"},
            {"", "", ""},
            {"Ejemplo", "", "TI | 1067123456 | Ana Maria | Rios Perez | F | 25/03/2012 | | 6 | A | Luz Perez | MADRE | 3001234567"},
            {"Importante", "", "Si el estudiante ya existe (mismo documento) se actualizan sus datos y su grupo"},
    };

    private ExcelEstudiantes() {
    }

    // ---------------------------------------------------------------- escribir

    public static byte[] plantilla() {

        try (Workbook libro = new XSSFWorkbook()) {

            Sheet hoja = libro.createSheet("Estudiantes");
            escribirEncabezado(libro, hoja, COLUMNAS);

            Sheet ayuda = libro.createSheet("Instrucciones");
            for (int i = 0; i < INSTRUCCIONES.length; i++) {
                Row fila = ayuda.createRow(i);
                for (int j = 0; j < INSTRUCCIONES[i].length; j++) {
                    fila.createCell(j).setCellValue(INSTRUCCIONES[i][j]);
                }
            }
            ayuda.setColumnWidth(0, 24 * 256);
            ayuda.setColumnWidth(1, 16 * 256);
            ayuda.setColumnWidth(2, 90 * 256);

            return aBytes(libro);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar la plantilla", e);
        }
    }

    // filas: cada fila en el mismo orden de las columnas
    public static byte[] exportar(String[] columnas, List<String[]> filas) {

        try (Workbook libro = new XSSFWorkbook()) {

            Sheet hoja = libro.createSheet("Estudiantes");
            escribirEncabezado(libro, hoja, columnas);

            int numero = 1;
            for (String[] datos : filas) {
                Row fila = hoja.createRow(numero);
                for (int j = 0; j < datos.length; j++) {
                    if (datos[j] != null) {
                        fila.createCell(j).setCellValue(datos[j]);
                    }
                }
                numero++;
            }

            return aBytes(libro);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el excel", e);
        }
    }

    private static void escribirEncabezado(Workbook libro, Sheet hoja, String[] columnas) {

        Font negrita = libro.createFont();
        negrita.setBold(true);
        negrita.setColor(IndexedColors.WHITE.getIndex());

        CellStyle estilo = libro.createCellStyle();
        estilo.setFont(negrita);
        estilo.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        // todo como texto: si no, excel convierte los documentos largos a 1,07E+09
        CellStyle texto = libro.createCellStyle();
        texto.setDataFormat(libro.createDataFormat().getFormat("@"));

        Row encabezado = hoja.createRow(0);
        for (int i = 0; i < columnas.length; i++) {
            Cell celda = encabezado.createCell(i);
            celda.setCellValue(columnas[i]);
            celda.setCellStyle(estilo);
            hoja.setColumnWidth(i, 20 * 256);
            hoja.setDefaultColumnStyle(i, texto);
        }
        hoja.createFreezePane(0, 1);
    }

    private static byte[] aBytes(Workbook libro) throws IOException {

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        libro.write(salida);
        return salida.toByteArray();
    }

    // ---------------------------------------------------------------- leer

    // cada fila como mapa columna -> texto. las filas vacias se saltan.
    // la clave "_fila" trae el numero de fila como lo ve el usuario en excel
    public static List<Map<String, String>> leer(InputStream archivo, int maximoFilas) {

        try (Workbook libro = new XSSFWorkbook(archivo)) {

            Sheet hoja = libro.getSheetAt(0);
            Row encabezado = hoja.getRow(0);

            if (encabezado == null) {
                throw ApiException.invalido("El archivo no tiene encabezados. Usa la plantilla");
            }

            // columna del excel -> nombre de la columna de la plantilla (el orden puede cambiar)
            Map<Integer, String> columnas = new HashMap<>();
            DataFormatter formato = new DataFormatter();

            for (Cell celda : encabezado) {
                String nombre = normalizarEncabezado(formato.formatCellValue(celda));
                for (String columna : COLUMNAS) {
                    if (columna.equals(nombre)) {
                        columnas.put(celda.getColumnIndex(), columna);
                    }
                }
            }

            if (!columnas.containsValue(NUMERO_DOCUMENTO) || !columnas.containsValue(GRADO)) {
                throw ApiException.invalido("El archivo no tiene las columnas de la plantilla. Descargala y usala");
            }

            List<Map<String, String>> filas = new ArrayList<>();

            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) {
                    continue;
                }

                Map<String, String> datos = new HashMap<>();
                boolean vacia = true;

                for (Map.Entry<Integer, String> columna : columnas.entrySet()) {
                    String valor = leerCelda(fila.getCell(columna.getKey()), formato);
                    if (!valor.isEmpty()) {
                        vacia = false;
                    }
                    datos.put(columna.getValue(), valor);
                }

                if (vacia) {
                    continue;
                }

                if (filas.size() >= maximoFilas) {
                    throw ApiException.invalido("El archivo tiene mas de " + maximoFilas + " estudiantes. Dividelo en varios");
                }

                datos.put("_fila", String.valueOf(i + 1));
                filas.add(datos);
            }

            return filas;
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException) {
                throw (ApiException) e;
            }
            throw ApiException.invalido("No se pudo leer el archivo. Debe ser un Excel (.xlsx)");
        }
    }

    // las fechas de excel vienen como numero, se pasan a dd/mm/aaaa
    private static String leerCelda(Cell celda, DataFormatter formato) {

        if (celda == null) {
            return "";
        }

        if (celda.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(celda)) {
            LocalDate fecha = celda.getLocalDateTimeCellValue().toLocalDate();
            return fecha.getDayOfMonth() + "/" + fecha.getMonthValue() + "/" + fecha.getYear();
        }

        return formato.formatCellValue(celda).trim();
    }

    private static String normalizarEncabezado(String texto) {

        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.trim().toUpperCase().replace(' ', '_');
    }
}
