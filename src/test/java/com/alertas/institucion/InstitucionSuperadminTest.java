package com.alertas.institucion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.alertas.auth.model.Rol;
import com.alertas.soporte.IntegracionTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class InstitucionSuperadminTest extends IntegracionTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    PasswordEncoder passwordEncoder;

    String tokenSa;

    @BeforeEach
    void entrarComoSuperadmin() throws Exception {

        String body = "{\"usuario\":\"" + SUPERADMIN_USUARIO + "\",\"contrasena\":\"" + SUPERADMIN_CONTRASENA + "\"}";

        MvcResult resultado = mvc.perform(post("/api/v1/superadmin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();

        tokenSa = "Bearer " + leer(resultado).get("token").asText();
    }

    private JsonNode leer(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString());
    }

    private String json(Object objeto) throws Exception {
        return mapper.writeValueAsString(objeto);
    }

    private Map<String, Object> datosInstitucion(String nombre, String slug) {

        return Map.of(
                "nombre", nombre,
                "slug", slug,
                "municipio", "Monteria",
                "departamento", "Cordoba");
    }

    private Map<String, Object> datosAdmin(String documento) {

        return Map.of(
                "tipoDoc", "CC",
                "nroDoc", documento,
                "nombres", "Ana",
                "apellidos", "Rios");
    }

    private JsonNode crearInstitucion(String nombre, String slug, String documentoAdmin) throws Exception {

        String body = json(Map.of(
                "institucion", datosInstitucion(nombre, slug),
                "administrador", datosAdmin(documentoAdmin)));

        MvcResult resultado = mvc.perform(post("/api/v1/superadmin/instituciones")
                        .header("Authorization", tokenSa)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();

        return leer(resultado);
    }

    @Test
    void loginConClaveMalaDa401() throws Exception {

        mvc.perform(post("/api/v1/superadmin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + SUPERADMIN_USUARIO + "\",\"contrasena\":\"mala\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Usuario o contrasena incorrectos"));

        mvc.perform(get("/api/v1/superadmin/auth/yo").header("Authorization", tokenSa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value(SUPERADMIN_USUARIO));
    }

    @Test
    void crearInstitucionDejaTodoListo() throws Exception {

        JsonNode creada = crearInstitucion("Colegio Completo", "colegio-completo", "10101010");
        long id = idInstitucion("colegio-completo");

        // la api no entrega ids, solo el slug y el codigo del admin
        assertThat(creada.get("institucion").has("id")).isFalse();
        assertThat(creada.get("administrador").has("usuarioId")).isFalse();
        assertThat(creada.get("administrador").get("codigo").asText()).hasSize(12);

        assertThat(creada.get("institucion").get("enlace").asText()).isEqualTo("http://localhost:5173/colegio-completo");
        // el admin no puede cambiar su contrasena, asi que no se le obliga
        assertThat(creada.get("administrador").get("debeCambiarContrasena").asBoolean()).isFalse();

        Integer grados = OWNER.queryForObject("SELECT count(*) FROM grados WHERE gra_ins_id = ?", Integer.class, id);
        Integer gradosActivos = OWNER.queryForObject(
                "SELECT count(*) FROM grados WHERE gra_ins_id = ? AND gra_activo", Integer.class, id);
        Integer anio = OWNER.queryForObject(
                "SELECT anl_anio FROM anios_lectivos WHERE anl_ins_id = ? AND anl_activo", Integer.class, id);
        Integer categorias = OWNER.queryForObject(
                "SELECT count(*) FROM categorias_alerta WHERE cat_ins_id = ?", Integer.class, id);

        assertThat(grados).isEqualTo(14);
        assertThat(gradosActivos).isEqualTo(12);
        assertThat(anio).isEqualTo(LocalDate.now().getYear());
        assertThat(categorias).isEqualTo(6);

        // usuario = documento y contrasena = documento, guardada con bcrypt
        String hash = OWNER.queryForObject(
                "SELECT usu_contrasena_hash FROM usuarios WHERE usu_ins_id = ? AND usu_usuario = '10101010' AND usu_rol = 'ADMIN'",
                String.class, id);
        assertThat(passwordEncoder.matches("10101010", hash)).isTrue();

        Integer bitacora = OWNER.queryForObject(
                "SELECT count(*) FROM bitacora WHERE bit_ins_id = ? AND bit_accion = 'CREAR_INSTITUCION' AND bit_sad_id IS NOT NULL",
                Integer.class, id);
        assertThat(bitacora).isEqualTo(1);
    }

    @Test
    void slugRepetidoReservadoOMalEscrito() throws Exception {

        crearInstitucion("Colegio Unico", "colegio-unico", "20202020");

        String repetido = json(Map.of(
                "institucion", datosInstitucion("Otro", "colegio-unico"),
                "administrador", datosAdmin("20202021")));

        mvc.perform(post("/api/v1/superadmin/instituciones").header("Authorization", tokenSa)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(repetido))
                .andExpect(status().isConflict());

        String reservado = json(Map.of(
                "institucion", datosInstitucion("Otro", "api"),
                "administrador", datosAdmin("20202022")));

        mvc.perform(post("/api/v1/superadmin/instituciones").header("Authorization", tokenSa)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(reservado))
                .andExpect(status().isBadRequest());

        String conEspacios = json(Map.of(
                "institucion", datosInstitucion("Otro", "Mi Colegio"),
                "administrador", datosAdmin("20202023")));

        mvc.perform(post("/api/v1/superadmin/instituciones").header("Authorization", tokenSa)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(conEspacios))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El enlace solo lleva minusculas, numeros y guiones, sin espacios"));
    }

    @Test
    void inhabilitarCortaElAccesoYHabilitarLoDevuelve() throws Exception {

        crearInstitucion("Colegio Pausa", "colegio-pausa", "30303030");
        long id = idInstitucion("colegio-pausa");
        long adminId = idUsuario(id, "30303030");
        String tokenAdmin = token(adminId, id, "colegio-pausa", Rol.ADMIN);

        mvc.perform(get("/api/v1/prueba/tenant").header("Authorization", tokenAdmin)).andExpect(status().isOk());

        mvc.perform(patch("/api/v1/superadmin/instituciones/colegio-pausa/inactivar").header("Authorization", tokenSa)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\" \"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/v1/superadmin/instituciones/colegio-pausa/inactivar").header("Authorization", tokenSa)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Fin de contrato\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(false))
                .andExpect(jsonPath("$.motivoInactivacion").value("Fin de contrato"));

        // de inmediato, sin esperar a que venza la cache
        mvc.perform(get("/api/v1/prueba/tenant").header("Authorization", tokenAdmin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/public/colegio-pausa/prueba")).andExpect(status().isNotFound());

        mvc.perform(patch("/api/v1/superadmin/instituciones/colegio-pausa/activar").header("Authorization", tokenSa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activa").value(true))
                .andExpect(jsonPath("$.motivoInactivacion").doesNotExist());

        mvc.perform(get("/api/v1/prueba/tenant").header("Authorization", tokenAdmin)).andExpect(status().isOk());
    }

    @Test
    void listarBuscaSinTildesYFiltraPorEstado() throws Exception {

        crearInstitucion("Institucion Educativa San José", "ie-san-jose", "40404040");
        crearInstitucion("Colegio Pío XII", "colegio-pio", "40404041");

        mvc.perform(patch("/api/v1/superadmin/instituciones/colegio-pio/inactivar")
                        .header("Authorization", tokenSa)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Prueba\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/superadmin/instituciones").param("texto", "SAN JOSE").header("Authorization", tokenSa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].slug").value("ie-san-jose"));

        mvc.perform(get("/api/v1/superadmin/instituciones").param("texto", "pio").param("activa", "false")
                        .header("Authorization", tokenSa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));

        mvc.perform(get("/api/v1/superadmin/instituciones").param("tamanio", "1000").header("Authorization", tokenSa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamanio").value(50));
    }

    @Test
    void restablecerEInactivarAdministradorCierraSusSesiones() throws Exception {

        JsonNode creada = crearInstitucion("Colegio Claves", "colegio-claves", "50505050");
        long id = idInstitucion("colegio-claves");
        long adminId = idUsuario(id, "50505050");
        String codigoAdmin = creada.get("administrador").get("codigo").asText();

        OWNER.update("UPDATE usuarios SET usu_contrasena_hash = 'otra', usu_debe_cambiar_contrasena = false WHERE usu_id = ?", adminId);
        String tokenViejo = token(adminId, id, "colegio-claves", Rol.ADMIN);
        Thread.sleep(5);

        mvc.perform(post("/api/v1/superadmin/instituciones/colegio-claves/administradores/" + codigoAdmin + "/restablecer-contrasena")
                        .header("Authorization", tokenSa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debeCambiarContrasena").value(false));

        String hash = OWNER.queryForObject("SELECT usu_contrasena_hash FROM usuarios WHERE usu_id = ?", String.class, adminId);
        assertThat(passwordEncoder.matches("50505050", hash)).isTrue();
        mvc.perform(get("/api/v1/prueba/tenant").header("Authorization", tokenViejo)).andExpect(status().isUnauthorized());

        Thread.sleep(5);
        String tokenNuevo = token(adminId, id, "colegio-claves", Rol.ADMIN);
        Thread.sleep(5);

        mvc.perform(patch("/api/v1/superadmin/instituciones/colegio-claves/administradores/" + codigoAdmin + "/estado")
                        .header("Authorization", tokenSa)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mvc.perform(get("/api/v1/prueba/tenant").header("Authorization", tokenNuevo)).andExpect(status().isUnauthorized());
    }

    @Test
    void elSuperadminEditaLosDatosDelAdministrador() throws Exception {

        JsonNode a = crearInstitucion("Colegio Editar A", "colegio-editar-a", "70707070");
        JsonNode b = crearInstitucion("Colegio Editar B", "colegio-editar-b", "70707080");
        String codigoAdmin = a.get("administrador").get("codigo").asText();
        String adminDeB = b.get("administrador").get("codigo").asText();
        String ruta = "/api/v1/superadmin/instituciones/colegio-editar-a/administradores/";

        // un segundo administrador en A, para probar el documento repetido
        mvc.perform(post(ruta.substring(0, ruta.length() - 1))
                        .header("Authorization", tokenSa)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(json(datosAdmin("70707071"))))
                .andExpect(status().isCreated());

        Map<String, Object> datos = new HashMap<>();
        datos.put("tipoDoc", "CC");
        datos.put("nroDoc", "70707072");
        datos.put("nombres", "Jaider");
        datos.put("apellidos", "Avila Correa");
        datos.put("correo", "jaider@colegio.edu.co");
        datos.put("celular", "3001234567");

        mvc.perform(put(ruta + codigoAdmin).header("Authorization", tokenSa)
                        .contentType(MediaType.APPLICATION_JSON).content(json(datos)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombres").value("Jaider"))
                .andExpect(jsonPath("$.apellidos").value("Avila Correa"))
                .andExpect(jsonPath("$.nroDoc").value("70707072"))
                .andExpect(jsonPath("$.correo").value("jaider@colegio.edu.co"));

        // entra con el documento nuevo y la misma contrasena de antes
        mvc.perform(post("/api/v1/public/colegio-editar-a/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"70707072\",\"contrasena\":\"70707070\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.nombres").value("Jaider"));
        mvc.perform(post("/api/v1/public/colegio-editar-a/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"70707070\",\"contrasena\":\"70707070\"}"))
                .andExpect(status().isUnauthorized());

        // documento de otro administrador del mismo colegio
        datos.put("nroDoc", "70707071");
        mvc.perform(put(ruta + codigoAdmin).header("Authorization", tokenSa)
                        .contentType(MediaType.APPLICATION_JSON).content(json(datos)))
                .andExpect(status().isConflict());

        // sin nombres
        datos.put("nroDoc", "70707072");
        datos.put("nombres", "");
        mvc.perform(put(ruta + codigoAdmin).header("Authorization", tokenSa)
                        .contentType(MediaType.APPLICATION_JSON).content(json(datos)))
                .andExpect(status().isBadRequest());

        // el admin de B no se edita entrando por A
        datos.put("nombres", "Otro");
        mvc.perform(put(ruta + adminDeB).header("Authorization", tokenSa)
                        .contentType(MediaType.APPLICATION_JSON).content(json(datos)))
                .andExpect(status().isNotFound());

        // y el admin del colegio no puede usar esta ruta
        long idA = idInstitucion("colegio-editar-a");
        String tokenAdmin = token(idUsuario(idA, "70707072"), idA, "colegio-editar-a", Rol.ADMIN);
        mvc.perform(put(ruta + codigoAdmin).header("Authorization", tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON).content(json(datos)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradoresDeUnaInstitucionNoSeMezclanConOtra() throws Exception {

        JsonNode a = crearInstitucion("Colegio Admin A", "colegio-admin-a", "60606060");
        JsonNode b = crearInstitucion("Colegio Admin B", "colegio-admin-b", "60606060");
        String adminDeB = b.get("administrador").get("codigo").asText();

        // mismo documento en dos colegios distintos si se permite
        assertThat(b.get("administrador").get("nroDoc").asText()).isEqualTo("60606060");

        mvc.perform(get("/api/v1/superadmin/instituciones/colegio-admin-a/administradores").header("Authorization", tokenSa))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // el admin de B no se puede tocar entrando por A
        mvc.perform(post("/api/v1/superadmin/instituciones/colegio-admin-a/administradores/" + adminDeB + "/restablecer-contrasena")
                        .header("Authorization", tokenSa))
                .andExpect(status().isNotFound());

        // documento repetido dentro del mismo colegio
        mvc.perform(post("/api/v1/superadmin/instituciones/colegio-admin-a/administradores")
                        .header("Authorization", tokenSa)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(json(datosAdmin("60606060"))))
                .andExpect(status().isConflict());
    }

    @Test
    void editarCambiaElEnlace() throws Exception {

        crearInstitucion("Colegio Viejo", "colegio-viejo", "70707070");

        mvc.perform(get("/api/v1/public/colegio-viejo/prueba")).andExpect(status().isOk());

        mvc.perform(put("/api/v1/superadmin/instituciones/colegio-viejo").header("Authorization", tokenSa)
                        .contentType(MediaType.APPLICATION_JSON).content(json(datosInstitucion("Colegio Nuevo", "colegio-nuevo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enlace").value("http://localhost:5173/colegio-nuevo"));

        mvc.perform(get("/api/v1/public/colegio-viejo/prueba")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/public/colegio-nuevo/prueba")).andExpect(status().isOk());
    }

    @Test
    void usuarioDeColegioNoUsaLasRutasDelSuperadmin() throws Exception {

        mvc.perform(get("/api/v1/superadmin/instituciones").header("Authorization", token(1L, 1L, "x", Rol.ADMIN)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/superadmin/instituciones")).andExpect(status().isUnauthorized());
    }
}
