package com.alertas.estudiante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class InactivacionEstudiantesTest extends IntegracionTest {

    static final String EXCEL = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    private byte[] excel(String[] encabezado, String[]... filas) throws Exception {

        try (Workbook libro = new XSSFWorkbook()) {
            Sheet hoja = libro.createSheet("Datos");
            Row primera = hoja.createRow(0);
            for (int i = 0; i < encabezado.length; i++) {
                primera.createCell(i).setCellValue(encabezado[i]);
            }
            for (int f = 0; f < filas.length; f++) {
                Row fila = hoja.createRow(f + 1);
                for (int c = 0; c < filas[f].length; c++) {
                    fila.createCell(c).setCellValue(filas[f][c]);
                }
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    // carga a los estudiantes con el excel normal
    private void cargar(Colegio colegio, String... documentos) throws Exception {

        String[][] filas = new String[documentos.length][];
        for (int i = 0; i < documentos.length; i++) {
            filas[i] = new String[] {"TI", documentos[i], "Estudiante", "Prueba", "", "", "", "6A"};
        }
        byte[] archivo = excel(new String[] {"TIPO_DOC", "NUMERO_DOCUMENTO", "NOMBRES", "APELLIDOS", "GENERO",
                "FECHA_NACIMIENTO", "CELULAR", "GRUPO"}, filas);

        MockMultipartFile parte = new MockMultipartFile("archivo", "estudiantes.xlsx", EXCEL, archivo);
        String token = leer(mvc.perform(multipart("/api/v1/estudiantes/importacion/vista-previa").file(parte)
                        .header("Authorization", colegio.admin()))
                .andExpect(status().isOk()).andReturn()).get("token").asText();
        mvc.perform(post("/api/v1/estudiantes/importacion/" + token + "/confirmar")
                        .header("Authorization", colegio.admin())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }

    private ResultActions vistaPrevia(Colegio colegio, String token, String[]... filas) throws Exception {

        byte[] archivo = excel(new String[] {"NUMERO_DOCUMENTO", "NOMBRE"}, filas);
        MockMultipartFile parte = new MockMultipartFile("archivo", "inactivar.xlsx", EXCEL, archivo);
        return mvc.perform(multipart("/api/v1/estudiantes/inactivacion/vista-previa").file(parte)
                .header("Authorization", token));
    }

    private ResultActions confirmar(Colegio colegio, String token) throws Exception {

        return mvc.perform(post("/api/v1/estudiantes/inactivacion/" + token + "/confirmar")
                .header("Authorization", colegio.admin())
                .header("Idempotency-Key", UUID.randomUUID().toString()));
    }

    @Test
    void laPlantillaTieneElDocumentoYElNombre() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "inac-plantilla", "91910000");

        byte[] contenido = mvc.perform(get("/api/v1/estudiantes/inactivacion/plantilla").header("Authorization", colegio.admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            assertThat(libro.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("NUMERO_DOCUMENTO");
            assertThat(libro.getSheetAt(0).getRow(0).getCell(1).getStringCellValue()).isEqualTo("NOMBRE");
        }
    }

    @Test
    void inactivaSoloALosDelArchivoPorSuDocumento() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "inac-ok", "92920000");
        cargar(colegio, "9201", "9202", "9203");

        // el nombre no se lee: aunque este mal escrito, cuenta el documento
        JsonNode previa = leer(vistaPrevia(colegio, colegio.admin(),
                        new String[] {"9201", "Nombre cualquiera"},
                        new String[] {"9.202", ""})
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seInactivan").value(2))
                .andExpect(jsonPath("$.yaInactivos").value(0))
                .andExpect(jsonPath("$.estudiantes[0].nroDoc").value("9201"))
                .andExpect(jsonPath("$.estudiantes[0].nombres").value("Estudiante"))
                .andExpect(jsonPath("$.totalErrores").value(0))
                .andReturn());

        String token = previa.get("token").asText();
        confirmar(colegio, token).andExpect(status().isOk()).andExpect(jsonPath("$.inactivados").value(2));
        // un token solo sirve una vez
        confirmar(colegio, token).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/estudiantes").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].nroDoc").value("9203"));

        // si se vuelve a subir, ya estan inactivos y no hay nada que confirmar
        vistaPrevia(colegio, colegio.admin(), new String[] {"9201", ""})
                .andExpect(jsonPath("$.seInactivan").value(0))
                .andExpect(jsonPath("$.yaInactivos").value(1))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void conErroresNoInactivaANadie() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "inac-errores", "93930000");
        cargar(colegio, "9301");

        vistaPrevia(colegio, colegio.admin(),
                        new String[] {"9301", "Bien"},
                        new String[] {"9999", "No existe"},
                        new String[] {"9301", "Repetido"},
                        new String[] {"93930000", "Es el admin"})
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.totalErrores").value(3))
                .andExpect(jsonPath("$.errores[0].fila").value(3))
                .andExpect(jsonPath("$.errores[0].mensaje").value("no hay un estudiante con el documento 9999"))
                .andExpect(jsonPath("$.errores[1].mensaje").value("el documento 9301 esta repetido en el archivo"))
                .andExpect(jsonPath("$.errores[2].mensaje").value("no hay un estudiante con el documento 93930000"));

        mvc.perform(get("/api/v1/estudiantes").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void elTokenDeUnColegioNoSirveEnOtro() throws Exception {

        Colegio a = crearColegioCompleto(mvc, mapper, "inac-token-a", "94940000");
        Colegio b = crearColegioCompleto(mvc, mapper, "inac-token-b", "94940001");
        cargar(a, "9401");

        String token = leer(vistaPrevia(a, a.admin(), new String[] {"9401", ""}).andReturn()).get("token").asText();
        confirmar(b, token).andExpect(status().isNotFound());
        confirmar(a, token).andExpect(jsonPath("$.inactivados").value(1));
    }
}
