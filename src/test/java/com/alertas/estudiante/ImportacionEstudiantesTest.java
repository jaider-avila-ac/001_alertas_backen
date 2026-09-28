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
import java.time.LocalDate;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class ImportacionEstudiantesTest extends IntegracionTest {

    static final int ESTE_ANIO = LocalDate.now().getYear();

    static final String[] ENCABEZADO = {
            "TIPO_DOC", "NUMERO_DOCUMENTO", "NOMBRES", "APELLIDOS", "GENERO", "FECHA_NACIMIENTO", "CELULAR",
            "GRADO", "GRUPO", "FAMILIAR_NOMBRE", "FAMILIAR_PARENTESCO", "FAMILIAR_CELULAR"
    };

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    // arma un xlsx como el que llenaria el colegio
    private byte[] excel(String[]... filas) throws Exception {

        try (Workbook libro = new XSSFWorkbook()) {
            Sheet hoja = libro.createSheet("Estudiantes");
            Row encabezado = hoja.createRow(0);
            for (int i = 0; i < ENCABEZADO.length; i++) {
                encabezado.createCell(i).setCellValue(ENCABEZADO[i]);
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

    private ResultActions vistaPrevia(Colegio colegio, byte[] archivo) throws Exception {

        MockMultipartFile parte = new MockMultipartFile("archivo", "estudiantes.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", archivo);
        return mvc.perform(multipart("/api/v1/estudiantes/importacion/vista-previa").file(parte)
                .header("Authorization", colegio.admin()));
    }

    private ResultActions confirmar(Colegio colegio, String token) throws Exception {

        return mvc.perform(post("/api/v1/estudiantes/importacion/" + token + "/confirmar")
                .header("Authorization", colegio.admin())
                .header("Idempotency-Key", UUID.randomUUID().toString()));
    }

    @Test
    void laPlantillaSeDescargaComoExcel() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "imp-plantilla", "81810000");

        MvcResult resultado = mvc.perform(get("/api/v1/estudiantes/importacion/plantilla").header("Authorization", colegio.admin()))
                .andExpect(status().isOk())
                .andReturn();

        byte[] contenido = resultado.getResponse().getContentAsByteArray();
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(contenido))) {
            assertThat(libro.getSheetAt(0).getRow(0).getCell(1).getStringCellValue()).isEqualTo("NUMERO_DOCUMENTO");
            assertThat(libro.getSheet("Instrucciones")).isNotNull();
        }
    }

    @Test
    void conErroresNoDejaConfirmarYDiceLaFila() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "imp-errores", "82820000");

        byte[] archivo = excel(
                new String[] {"TI", "8001", "Ana", "Rios", "F", "25/03/2012", "", "6", "A", "", "", ""},
                new String[] {"XX", "8002", "Luis", "Gil", "", "", "", "6", "A", "", "", ""},
                new String[] {"TI", "8003", "Pedro", "", "", "", "", "Prejardin", "A", "", "", ""},
                new String[] {"TI", "8001", "Repetido", "Rios", "", "", "", "6", "B", "", "", ""},
                new String[] {"TI", "82820000", "Es el admin", "X", "", "", "", "6", "A", "", "", ""});

        vistaPrevia(colegio, archivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.totalErrores").value(4))
                .andExpect(jsonPath("$.errores[0].fila").value(3))
                .andExpect(jsonPath("$.errores[0].mensaje").value("tipo de documento no valido (RC, TI, CC, CE o PPT)"))
                .andExpect(jsonPath("$.errores[1].fila").value(4))
                .andExpect(jsonPath("$.errores[2].mensaje").value("el documento 8001 esta repetido en el archivo"))
                .andExpect(jsonPath("$.errores[3].mensaje").value("el documento 82820000 ya lo usa un docente, psicorientador o administrador"));

        Integer creados = OWNER.queryForObject("SELECT count(*) FROM estudiantes WHERE est_ins_id = ?", Integer.class, colegio.id());
        assertThat(creados).isZero();
    }

    @Test
    void importaCreaGruposYLuegoActualiza() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "imp-ok", "83830000");

        byte[] archivo = excel(
                new String[] {"TI", "9001", "Ana María", "Ríos", "F", "25/03/2012", "3001112233", "6", "A", "Luz Pérez", "MADRE", "3009998877"},
                new String[] {"ti", "9002", "Luis", "Gil", "M", "", "", "6°", "a", "", "", ""},
                new String[] {"RC", "9003", "Sara", "Mesa", "", "", "", "Septimo", "B", "", "", ""});

        JsonNode previa = leer(vistaPrevia(colegio, archivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nuevos").value(3))
                .andExpect(jsonPath("$.actualizados").value(0))
                .andExpect(jsonPath("$.gruposACrear.length()").value(2))
                .andReturn());

        String token = previa.get("token").asText();

        confirmar(colegio, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creados").value(3))
                .andExpect(jsonPath("$.gruposCreados").value(2));

        // un token solo sirve una vez
        confirmar(colegio, token).andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/estudiantes").param("texto", "ana maria").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.contenido[0].gradoNombre").value("Sexto"))
                .andExpect(jsonPath("$.contenido[0].grupoNombre").value("A"));

        Integer familiares = OWNER.queryForObject("""
                SELECT count(*) FROM familiares f JOIN estudiantes e ON e.est_id = f.fam_est_id
                WHERE e.est_ins_id = ? AND e.est_nro_doc = '9001' AND f.fam_recibe_sms""", Integer.class, colegio.id());
        assertThat(familiares).isEqualTo(1);

        // segunda carga: Luis cambia de grupo y aparece uno nuevo
        byte[] otroArchivo = excel(
                new String[] {"TI", "9002", "Luis", "Gil", "M", "", "", "6", "B", "", "", ""},
                new String[] {"TI", "9004", "Nuevo", "Estudiante", "", "", "", "6", "A", "", "", ""});

        JsonNode segunda = leer(vistaPrevia(colegio, otroArchivo)
                .andExpect(jsonPath("$.nuevos").value(1))
                .andExpect(jsonPath("$.actualizados").value(1))
                .andExpect(jsonPath("$.gruposACrear[0]").value("Sexto B"))
                .andReturn());

        confirmar(colegio, segunda.get("token").asText())
                .andExpect(jsonPath("$.creados").value(1))
                .andExpect(jsonPath("$.actualizados").value(1));

        mvc.perform(get("/api/v1/estudiantes").param("texto", "luis").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.contenido[0].grupoNombre").value("B"));
    }

    @Test
    void elTokenDeUnColegioNoSirveEnOtro() throws Exception {

        Colegio a = crearColegioCompleto(mvc, mapper, "imp-token-a", "84840000");
        Colegio b = crearColegioCompleto(mvc, mapper, "imp-token-b", "84840001");

        byte[] archivo = excel(new String[] {"TI", "9101", "Ana", "Rios", "", "", "", "6", "A", "", "", ""});
        String token = leer(vistaPrevia(a, archivo).andReturn()).get("token").asText();

        confirmar(b, token).andExpect(status().isNotFound());
        confirmar(a, token).andExpect(status().isOk());
    }

    @Test
    void unArchivoQueNoEsLaPlantillaSeRechaza() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "imp-malo", "85850000");

        MockMultipartFile texto = new MockMultipartFile("archivo", "lista.xlsx", MediaType.TEXT_PLAIN_VALUE, "hola".getBytes());
        mvc.perform(multipart("/api/v1/estudiantes/importacion/vista-previa").file(texto).header("Authorization", colegio.admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No se pudo leer el archivo. Debe ser un Excel (.xlsx)"));
    }

    @Test
    void exportarDevuelveUnExcel() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "imp-exportar", "86860000");
        byte[] archivo = excel(new String[] {"TI", "9201", "Ana", "Rios", "F", "", "", "6", "A", "Luz Rios", "MADRE", "3001112233"});
        String token = leer(vistaPrevia(colegio, archivo).andReturn()).get("token").asText();
        confirmar(colegio, token).andExpect(status().isOk());

        byte[] exportado = mvc.perform(get("/api/v1/estudiantes/exportar").header("Authorization", colegio.admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // las mismas columnas de la plantilla: sirve de base para el anio siguiente
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(exportado))) {
            Row encabezado = libro.getSheetAt(0).getRow(0);
            for (int i = 0; i < ENCABEZADO.length; i++) {
                assertThat(encabezado.getCell(i).getStringCellValue()).isEqualTo(ENCABEZADO[i]);
            }
            Row fila = libro.getSheetAt(0).getRow(1);
            assertThat(fila.getCell(1).getStringCellValue()).isEqualTo("9201");
            assertThat(fila.getCell(4).getStringCellValue()).isEqualTo("F");
            assertThat(fila.getCell(7).getStringCellValue()).isEqualTo("Sexto");
            assertThat(fila.getCell(10).getStringCellValue()).isEqualTo("MADRE");
        }

        // subir el mismo archivo exportado funciona
        String otro = leer(vistaPrevia(colegio, exportado).andExpect(status().isOk()).andReturn()).get("token").asText();
        confirmar(colegio, otro).andExpect(jsonPath("$.actualizados").value(1));
    }

    @Test
    void unaCeldaVaciaNoBorraLoQueYaTenia() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "imp-vacias", "87870000");

        byte[] primero = excel(new String[] {"TI", "9301", "Ana", "Rios", "F", "25/03/2012", "3001112233", "6", "A", "", "", ""});
        confirmar(colegio, leer(vistaPrevia(colegio, primero).andReturn()).get("token").asText()).andExpect(status().isOk());

        // segunda carga sin genero, fecha ni celular
        byte[] segundo = excel(new String[] {"TI", "9301", "Ana", "Rios", "", "", "", "6", "A", "", "", ""});
        confirmar(colegio, leer(vistaPrevia(colegio, segundo).andReturn()).get("token").asText()).andExpect(status().isOk());

        String genero = OWNER.queryForObject("SELECT est_genero FROM estudiantes WHERE est_ins_id = ? AND est_nro_doc = '9301'",
                String.class, colegio.id());
        String celular = OWNER.queryForObject("SELECT est_celular FROM estudiantes WHERE est_ins_id = ? AND est_nro_doc = '9301'",
                String.class, colegio.id());
        assertThat(genero).isEqualTo("F");
        assertThat(celular).isEqualTo("3001112233");
    }
}
