package com.alertas.shared.excel;

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

// leer y escribir excel: importaciones (estudiantes, personal) y reportes. aqui no hay reglas de negocio
public final class ArchivoExcel {

    // en cada fila leida, el numero de fila como lo ve el usuario en excel
    public static final String NUMERO_FILA = "_fila";

    private ArchivoExcel() {
    }

    // ---------------------------------------------------------------- escribir

    // hoja de datos con encabezado y una segunda hoja con las instrucciones
    public static byte[] plantilla(String nombreHoja, String[] columnas, String[][] instrucciones) {

        try (Workbook libro = new XSSFWorkbook()) {

            Sheet hoja = libro.createSheet(nombreHoja);
            escribirEncabezado(libro, hoja, columnas);

            Sheet ayuda = libro.createSheet("Instrucciones");
            for (int i = 0; i < instrucciones.length; i++) {
                Row fila = ayuda.createRow(i);
                for (int j = 0; j < instrucciones[i].length; j++) {
                    fila.createCell(j).setCellValue(instrucciones[i][j]);
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

    // filas: cada una en el mismo orden de las columnas
    public static byte[] exportar(String nombreHoja, String[] columnas, List<String[]> filas) {

        try (Workbook libro = new XSSFWorkbook()) {

            Sheet hoja = libro.createSheet(nombreHoja);
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

    // reporte de varias hojas (estadisticas). aqui los numeros se dejan como numero para poder sumarlos
    public static byte[] reporte(List<HojaExcel> hojas) {

        try (Workbook libro = new XSSFWorkbook()) {

            CellStyle estiloEncabezado = estiloEncabezado(libro);

            for (HojaExcel datos : hojas) {
                Sheet hoja = libro.createSheet(datos.nombre());

                Row encabezado = hoja.createRow(0);
                for (int i = 0; i < datos.columnas().length; i++) {
                    Cell celda = encabezado.createCell(i);
                    celda.setCellValue(datos.columnas()[i]);
                    celda.setCellStyle(estiloEncabezado);
                    hoja.setColumnWidth(i, 28 * 256);
                }

                int numero = 1;
                for (Object[] valores : datos.filas()) {
                    Row fila = hoja.createRow(numero);
                    for (int j = 0; j < valores.length; j++) {
                        if (valores[j] instanceof Number) {
                            fila.createCell(j).setCellValue(((Number) valores[j]).doubleValue());
                        } else if (valores[j] != null) {
                            fila.createCell(j).setCellValue(String.valueOf(valores[j]));
                        }
                    }
                    numero++;
                }
                hoja.createFreezePane(0, 1);
            }

            return aBytes(libro);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo generar el excel", e);
        }
    }

    private static CellStyle estiloEncabezado(Workbook libro) {

        Font negrita = libro.createFont();
        negrita.setBold(true);
        negrita.setColor(IndexedColors.WHITE.getIndex());

        CellStyle estilo = libro.createCellStyle();
        estilo.setFont(negrita);
        estilo.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return estilo;
    }

    private static void escribirEncabezado(Workbook libro, Sheet hoja, String[] columnas) {

        CellStyle estilo = estiloEncabezado(libro);

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

    // cada fila como mapa columna -> texto; las vacias se saltan. las columnas se buscan por su
    // nombre en el encabezado, asi no importa el orden. obligatorias: sin ellas no es la plantilla
    public static List<Map<String, String>> leer(
            InputStream archivo, String[] columnas, String[] obligatorias, int maximoFilas) {

        try (Workbook libro = new XSSFWorkbook(archivo)) {

            Sheet hoja = libro.getSheetAt(0);
            Row encabezado = hoja.getRow(0);

            if (encabezado == null) {
                throw ApiException.invalido("El archivo no tiene encabezados. Usa la plantilla");
            }

            Map<Integer, String> posiciones = new HashMap<>();
            DataFormatter formato = new DataFormatter();

            for (Cell celda : encabezado) {
                String nombre = normalizarEncabezado(formato.formatCellValue(celda));
                for (String columna : columnas) {
                    if (columna.equals(nombre)) {
                        posiciones.put(celda.getColumnIndex(), columna);
                    }
                }
            }

            for (String obligatoria : obligatorias) {
                if (!posiciones.containsValue(obligatoria)) {
                    throw ApiException.invalido("El archivo no tiene las columnas de la plantilla. Descargala y usala");
                }
            }

            List<Map<String, String>> filas = new ArrayList<>();

            for (int i = 1; i <= hoja.getLastRowNum(); i++) {
                Row fila = hoja.getRow(i);
                if (fila == null) {
                    continue;
                }

                Map<String, String> datos = new HashMap<>();
                boolean vacia = true;

                for (Map.Entry<Integer, String> columna : posiciones.entrySet()) {
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
                    throw ApiException.invalido("El archivo tiene mas de " + maximoFilas + " filas. Dividelo en varios");
                }

                datos.put(NUMERO_FILA, String.valueOf(i + 1));
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
