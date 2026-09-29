package com.alertas.shared.excel;

import java.util.List;

// una hoja de un reporte: cada fila en el orden de las columnas. los Number quedan como numero
public record HojaExcel(String nombre, String[] columnas, List<Object[]> filas) {
}
