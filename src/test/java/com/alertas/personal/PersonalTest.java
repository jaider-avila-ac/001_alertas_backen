package com.alertas.personal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

class PersonalTest extends IntegracionTest {

    static final String[] ENCABEZADO = {"TIPO_DOC", "NUMERO_DOCUMENTO", "NOMBRES", "APELLIDOS", "ROL", "CORREO", "CELULAR"};

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    private String persona(String documento, String nombres, String rol) {
        return "{\"tipoDoc\":\"CC\",\"nroDoc\":\"" + documento + "\",\"nombres\":\"" + nombres
                + "\",\"apellidos\":\"Prueba\",\"rol\":\"" + rol + "\"}";
    }

    private ResultActions crear(Colegio colegio, String body) throws Exception {

        return mvc.perform(post("/api/v1/personal").header("Authorization", colegio.admin())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String crearYDarCodigo(Colegio colegio, String documento, String nombres, String rol) throws Exception {
        return leer(crear(colegio, persona(documento, nombres, rol)).andExpect(status().isCreated()).andReturn())
                .get("codigo").asText();
    }

    private String login(String slug, String usuario, String contrasena) throws Exception {

        MvcResult r = mvc.perform(post("/api/v1/public/" + slug + "/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + usuario + "\",\"contrasena\":\"" + contrasena + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + leer(r).get("token").asText();
    }

    @Test
    void crearListarYEditar() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "per-crud", "71710000");

        String codigo = leer(crear(colegio, persona("5001", "Carlos", "DOCENTE"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rol").value("DOCENTE"))
                .andExpect(jsonPath("$.debeCambiarContrasena").value(true))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andReturn()).get("codigo").asText();

        crearYDarCodigo(colegio, "5002", "Marta", "PSICORIENTADOR");

        // documento repetido, o el del admin
        crear(colegio, persona("5001", "Otro", "DOCENTE")).andExpect(status().isConflict());
        crear(colegio, persona("71710000", "Otro", "DOCENTE")).andExpect(status().isConflict());
        // por aqui no se crean administradores
        crear(colegio, persona("5003", "Otro", "ADMIN")).andExpect(status().isBadRequest());

        // el admin no sale en el listado
        mvc.perform(get("/api/v1/personal").header("Authorization", colegio.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2));

        // paginado en el backend
        mvc.perform(get("/api/v1/personal").param("tamanio", "1").param("pagina", "1").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.contenido.length()").value(1))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(2));

        mvc.perform(get("/api/v1/personal").param("rol", "PSICORIENTADOR").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].nombres").value("Marta"));

        mvc.perform(get("/api/v1/personal").param("texto", "carlos").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.contenido[0].codigo").value(codigo));

        // corrige el documento; aunque manden otro rol, el rol no cambia
        mvc.perform(put("/api/v1/personal/" + codigo).header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content(persona("5009", "Carlos", "PSICORIENTADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nroDoc").value("5009"))
                .andExpect(jsonPath("$.rol").value("DOCENTE"));

        String rol = OWNER.queryForObject("SELECT usu_rol FROM usuarios WHERE usu_ins_id = ? AND usu_usuario = '5009'",
                String.class, colegio.id());
        assertThat(rol).isEqualTo("DOCENTE");
    }

    @Test
    void elAdminYLasPersonasDeOtroColegioNoExistenPorAqui() throws Exception {

        Colegio a = crearColegioCompleto(mvc, mapper, "per-aislado-a", "72720000");
        Colegio b = crearColegioCompleto(mvc, mapper, "per-aislado-b", "72720001");

        String deB = crearYDarCodigo(b, "6001", "Ajeno", "DOCENTE");
        String codigoAdminA = OWNER.queryForObject("SELECT per_codigo FROM personal WHERE per_ins_id = ? AND per_nro_doc = '72720000'",
                String.class, a.id());

        mvc.perform(get("/api/v1/personal/" + deB).header("Authorization", a.admin())).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/personal/" + deB + "/restablecer-contrasena").header("Authorization", a.admin()))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/personal/" + codigoAdminA).header("Authorization", a.admin())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/personal/" + codigoAdminA + "/estado").header("Authorization", a.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isNotFound());

        // un docente no maneja el personal
        String docente = token(idUsuario(b.id(), "6001"), b.id(), b.slug(), Rol.DOCENTE);
        mvc.perform(get("/api/v1/personal").header("Authorization", docente)).andExpect(status().isForbidden());
    }

    @Test
    void restablecerCierraLaSesionYObligaACambiar() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "per-restablecer", "73730000");
        String codigo = crearYDarCodigo(colegio, "7001", "Lina", "PSICORIENTADOR");

        // entra y pone su clave
        String primera = login(colegio.slug(), "7001", "7001");
        MvcResult cambio = mvc.perform(put("/api/v1/auth/contrasena").header("Authorization", primera)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"actual\":\"7001\",\"nueva\":\"clave-de-lina\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String sesion = "Bearer " + leer(cambio).get("token").asText();
        Thread.sleep(5);

        mvc.perform(post("/api/v1/personal/" + codigo + "/restablecer-contrasena").header("Authorization", colegio.admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarContrasena").value(true));

        mvc.perform(get("/api/v1/auth/yo").header("Authorization", sesion)).andExpect(status().isUnauthorized());
        login(colegio.slug(), "7001", "7001");
    }

    @Test
    void activarEInactivarEnBloque() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "per-masivo", "74740000");
        String d1 = crearYDarCodigo(colegio, "8001", "Uno", "DOCENTE");
        crearYDarCodigo(colegio, "8002", "Dos", "DOCENTE");
        String p1 = crearYDarCodigo(colegio, "8003", "Tres", "PSICORIENTADOR");
        String codigoAdmin = OWNER.queryForObject("SELECT per_codigo FROM personal WHERE per_ins_id = ? AND per_nro_doc = '74740000'",
                String.class, colegio.id());

        String sesionDocente = token(idUsuario(colegio.id(), "8001"), colegio.id(), colegio.slug(), Rol.DOCENTE);
        Thread.sleep(5);

        mvc.perform(patch("/api/v1/personal/estado-masivo").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"DOCENTE\",\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afectados").value(2));

        mvc.perform(get("/api/v1/auth/yo").header("Authorization", sesionDocente)).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/personal").param("estado", "inactivos").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.totalElementos").value(2));

        // por seleccion: el codigo del admin se ignora
        String codigos = "[\"" + d1 + "\",\"" + codigoAdmin + "\"]";
        mvc.perform(patch("/api/v1/personal/estado-masivo").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"codigos\":" + codigos + ",\"activo\":true}"))
                .andExpect(jsonPath("$.afectados").value(1));

        // solo cuenta a quienes cambian: el 8002 ya estaba inactivo
        mvc.perform(patch("/api/v1/personal/estado-masivo").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"todos\":true,\"activo\":false}"))
                .andExpect(jsonPath("$.afectados").value(2));

        Boolean adminActivo = OWNER.queryForObject(
                "SELECT usu_activo FROM usuarios WHERE usu_ins_id = ? AND usu_usuario = '74740000'", Boolean.class, colegio.id());
        assertThat(adminActivo).isTrue();

        mvc.perform(patch("/api/v1/personal/estado-masivo").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"ADMIN\",\"activo\":false}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/v1/personal/" + p1 + "/estado").header("Authorization", colegio.admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(true));
    }

    // ---------------------------------------------------------------- importacion

    private byte[] excel(String[]... filas) throws Exception {

        try (Workbook libro = new XSSFWorkbook()) {
            Sheet hoja = libro.createSheet("Personal");
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

        MockMultipartFile parte = new MockMultipartFile("archivo", "personal.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", archivo);
        return mvc.perform(multipart("/api/v1/personal/importacion/vista-previa").file(parte)
                .header("Authorization", colegio.admin()));
    }

    private ResultActions confirmar(Colegio colegio, String token) throws Exception {

        return mvc.perform(post("/api/v1/personal/importacion/" + token + "/confirmar")
                .header("Authorization", colegio.admin())
                .header("Idempotency-Key", UUID.randomUUID().toString()));
    }

    @Test
    void importarConErroresNoDejaConfirmar() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "per-imp-errores", "75750000");
        crearUsuario(colegio.id(), "9990", Rol.ESTUDIANTE, "x", false);

        byte[] archivo = excel(
                new String[] {"CC", "9001", "Ana", "Rios", "DOCENTE", "", ""},
                new String[] {"CC", "9002", "Luis", "Gil", "RECTOR", "", ""},
                new String[] {"CC", "75750000", "Es el admin", "X", "DOCENTE", "", ""},
                new String[] {"CC", "9990", "Es estudiante", "X", "DOCENTE", "", ""},
                new String[] {"CC", "9001", "Repetida", "Rios", "DOCENTE", "", ""});

        vistaPrevia(colegio, archivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.totalErrores").value(4))
                .andExpect(jsonPath("$.errores[0].fila").value(3))
                .andExpect(jsonPath("$.errores[0].mensaje").value("rol no valido (DOCENTE o PSICORIENTADOR)"))
                .andExpect(jsonPath("$.errores[1].mensaje").value(
                        "el documento 75750000 es de un administrador, sus datos los cambia el superadmin"))
                .andExpect(jsonPath("$.errores[2].mensaje").value("el documento 9990 ya lo usa un estudiante"))
                .andExpect(jsonPath("$.errores[3].mensaje").value("el documento 9001 esta repetido en el archivo"));
    }

    @Test
    void importarCreaYLuegoActualizaSinCambiarElRol() throws Exception {

        Colegio colegio = crearColegioCompleto(mvc, mapper, "per-imp-ok", "76760000");

        byte[] archivo = excel(
                new String[] {"CC", "9101", "Ana", "Rios", "docente", "ana@correo.com", "3001112233"},
                new String[] {"cc", "9102", "Luis", "Gil", "Orientador", "", ""});

        String token = leer(vistaPrevia(colegio, archivo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nuevos").value(2))
                .andExpect(jsonPath("$.actualizados").value(0))
                .andReturn()).get("token").asText();

        confirmar(colegio, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creados").value(2));
        confirmar(colegio, token).andExpect(status().isNotFound());

        // entran con su documento
        login(colegio.slug(), "9102", "9102");

        // segunda carga: el rol no se puede cambiar
        byte[] otroRol = excel(new String[] {"CC", "9101", "Ana", "Rios", "PSICORIENTADOR", "", ""});
        vistaPrevia(colegio, otroRol)
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.errores[0].mensaje").value(
                        "el documento 9101 ya esta registrado como docente, el rol no se puede cambiar"));

        // con el mismo rol se actualizan los datos
        byte[] otro = excel(new String[] {"CC", "9101", "Ana Lucia", "Rios", "DOCENTE", "", ""});
        String segundo = leer(vistaPrevia(colegio, otro)
                .andExpect(jsonPath("$.nuevos").value(0))
                .andExpect(jsonPath("$.actualizados").value(1))
                .andReturn()).get("token").asText();
        confirmar(colegio, segundo).andExpect(jsonPath("$.actualizados").value(1));

        mvc.perform(get("/api/v1/personal").param("texto", "ana").header("Authorization", colegio.admin()))
                .andExpect(jsonPath("$.contenido[0].nombres").value("Ana Lucia"))
                .andExpect(jsonPath("$.contenido[0].rol").value("DOCENTE"))
                .andExpect(jsonPath("$.contenido[0].correo").value("ana@correo.com"));

        byte[] exportado = mvc.perform(get("/api/v1/personal/exportar").header("Authorization", colegio.admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(exportado))) {
            assertThat(libro.getSheetAt(0).getLastRowNum()).isEqualTo(2);
        }

        MvcResult plantilla = mvc.perform(get("/api/v1/personal/importacion/plantilla").header("Authorization", colegio.admin()))
                .andExpect(status().isOk())
                .andReturn();
        try (Workbook libro = new XSSFWorkbook(new ByteArrayInputStream(plantilla.getResponse().getContentAsByteArray()))) {
            assertThat(libro.getSheetAt(0).getRow(0).getCell(4).getStringCellValue()).isEqualTo("ROL");
        }
    }
}
