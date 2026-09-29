package com.alertas.soporte;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.alertas.auth.model.Rol;
import com.alertas.auth.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

// postgres y redis reales. la bd se arma con los mismos scripts de db/ y el backend
// se conecta con alertas_app, asi las pruebas pasan por RLS igual que en produccion
@SpringBootTest
@AutoConfigureMockMvc
public abstract class IntegracionTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("alertas");
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withCommand("redis-server", "--requirepass", "clave-redis")
            .withExposedPorts(6379);

    protected static final String SUPERADMIN_USUARIO = "sa-pruebas";
    protected static final String SUPERADMIN_CONTRASENA = "clave-superadmin-1";

    static final String CLAVE_OWNER = "clave-owner";
    static final String CLAVE_APP = "clave-app";

    // el owner no tiene RLS, sirve para meter datos de prueba en cualquier institucion
    protected static final JdbcTemplate OWNER;

    static {
        POSTGRES.start();
        REDIS.start();
        prepararBaseDeDatos();
        OWNER = new JdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "alertas_owner", CLAVE_OWNER));
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", () -> "alertas_app");
        r.add("spring.datasource.password", () -> CLAVE_APP);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("spring.data.redis.password", () -> "clave-redis");
        r.add("app.jwt.secreto", () -> "clave-de-pruebas-que-tiene-mas-de-32-caracteres");
        r.add("app.superadmin.usuario", () -> SUPERADMIN_USUARIO);
        r.add("app.superadmin.contrasena", () -> SUPERADMIN_CONTRASENA);
        // nunca sms reales en las pruebas, aunque el .env tenga llaves de Twilio
        r.add("app.sms.twilio.sid", () -> "");
        r.add("app.sms.twilio.token", () -> "");
        r.add("app.sms.twilio.numero", () -> "");
    }

    @Autowired
    protected JwtService jwtService;

    protected static Long crearInstitucion(String slug) {
        return crearInstitucion(slug, true, true);
    }

    protected static Long crearInstitucion(String slug, boolean activa, boolean accesoEstudiantes) {
        return OWNER.queryForObject("""
                INSERT INTO instituciones (ins_nombre, ins_slug, ins_activa, ins_acceso_estudiantes,
                                           ins_inactivada_en, ins_motivo_inactivacion)
                VALUES (?, ?, ?, ?, CASE WHEN ? THEN NULL ELSE now() END, CASE WHEN ? THEN NULL ELSE 'prueba' END)
                RETURNING ins_id""", Long.class, "Colegio " + slug, slug, activa, accesoEstudiantes, activa, activa);
    }

    protected String token(Long usuarioId, Long institucionId, String slug, Rol rol) {
        return "Bearer " + jwtService.generar(usuarioId, institucionId, slug, rol);
    }

    // colegio creado por la api del superadmin (con grados, anio activo, categorias y admin).
    // admin es el token del administrador listo para usar
    public record Colegio(Long id, String slug, String admin) {
    }

    protected Colegio crearColegioCompleto(MockMvc mvc, ObjectMapper mapper, String slug, String documentoAdmin)
            throws Exception {

        MvcResult login = mvc.perform(post("/api/v1/superadmin/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"" + SUPERADMIN_USUARIO + "\",\"contrasena\":\"" + SUPERADMIN_CONTRASENA + "\"}"))
                .andReturn();
        String sa = "Bearer " + mapper.readTree(login.getResponse().getContentAsString()).get("token").asText();

        String body = mapper.writeValueAsString(Map.of(
                "institucion", Map.of("nombre", "Colegio " + slug, "slug", slug),
                "administrador", Map.of("tipoDoc", "CC", "nroDoc", documentoAdmin, "nombres", "Admin", "apellidos", "Prueba")));

        MvcResult creada = mvc.perform(post("/api/v1/superadmin/instituciones").header("Authorization", sa)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn();

        if (creada.getResponse().getStatus() != 201) {
            throw new IllegalStateException("No se creo el colegio: " + creada.getResponse().getContentAsString());
        }

        Long id = idInstitucion(slug);
        Long adminId = idUsuario(id, documentoAdmin);
        return new Colegio(id, slug, token(adminId, id, slug, Rol.ADMIN));
    }

    // la api ya no devuelve ids, las pruebas los sacan de la bd para armar tokens
    protected static Long idInstitucion(String slug) {
        return OWNER.queryForObject("SELECT ins_id FROM instituciones WHERE ins_slug = ?", Long.class, slug);
    }

    protected static Long idUsuario(Long institucionId, String documento) {
        return OWNER.queryForObject("SELECT usu_id FROM usuarios WHERE usu_ins_id = ? AND usu_usuario = ?",
                Long.class, institucionId, documento);
    }

    // usuario con contrasena = documento, como los crea el sistema. devuelve el id
    protected static Long crearUsuario(Long institucionId, String documento, Rol rol, String hash, boolean debeCambiar) {
        return OWNER.queryForObject("""
                INSERT INTO usuarios (usu_ins_id, usu_usuario, usu_contrasena_hash, usu_rol, usu_debe_cambiar_contrasena)
                VALUES (?, ?, ?, ?, ?) RETURNING usu_id""", Long.class, institucionId, documento, hash, rol.name(), debeCambiar);
    }

    private static void prepararBaseDeDatos() {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement st = c.createStatement()) {
            // lo mismo que hace db/init/00_roles.sh
            st.execute("CREATE ROLE alertas_owner LOGIN PASSWORD '" + CLAVE_OWNER + "'");
            st.execute("CREATE ROLE alertas_app LOGIN PASSWORD '" + CLAVE_APP + "'");
            st.execute("ALTER DATABASE alertas OWNER TO alertas_owner");
            st.execute("ALTER SCHEMA public OWNER TO alertas_owner");
            st.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
            st.execute("GRANT USAGE ON SCHEMA public TO alertas_app");
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudieron crear los roles", e);
        }

        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "alertas_owner", CLAVE_OWNER);
             Statement st = c.createStatement()) {

            for (Path script : migraciones().values()) {
                st.execute(Files.readString(script));
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Fallo aplicando las migraciones", e);
        }
    }

    // ordenadas por numero: V2 antes que V10
    private static TreeMap<Integer, Path> migraciones() throws IOException {

        TreeMap<Integer, Path> ordenadas = new TreeMap<>();

        try (DirectoryStream<Path> archivos = Files.newDirectoryStream(Path.of("db", "migration"), "V*__*.sql")) {
            for (Path archivo : archivos) {
                String nombre = archivo.getFileName().toString();
                int numero = Integer.parseInt(nombre.substring(1, nombre.indexOf("__")));
                ordenadas.put(numero, archivo);
            }
        }

        return ordenadas;
    }
}
