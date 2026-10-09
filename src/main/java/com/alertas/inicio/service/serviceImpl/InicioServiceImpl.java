package com.alertas.inicio.service.serviceImpl;

import com.alertas.auth.model.Rol;
import com.alertas.auth.model.UsuarioAutenticado;
import com.alertas.estadistica.dto.ConteoResponse;
import com.alertas.inicio.dto.ElementoResponse;
import com.alertas.inicio.dto.InicioResponse;
import com.alertas.inicio.dto.TarjetaResponse;
import com.alertas.inicio.repository.InicioRepository;
import com.alertas.inicio.service.InicioService;
import com.alertas.shared.Fechas;
import com.alertas.shared.TenantSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InicioServiceImpl implements InicioService {

    private static final String[][] NIVELES = {
        {"LEVE", "Leve"}, {"MODERADO", "Moderado"}, {"ALTO", "Alto"}, {"CRITICO", "Critico"}};

    private final InicioRepository repository;
    private final ObjectMapper mapper;
    private final EntityManager em;

    public InicioServiceImpl(InicioRepository repository, ObjectMapper mapper, EntityManager em) {
        this.repository = repository;
        this.mapper = mapper;
        this.em = em;
    }

    // maximo tres consultas: la del colegio (RLS) y una o dos de datos segun el rol
    @Override
    @Transactional(readOnly = true)
    public InicioResponse dashboard() {

        TenantSupport.requireTenant(em);
        UsuarioAutenticado usuario = UsuarioAutenticado.actual();
        String zona = ZoneId.systemDefault().getId();

        if (usuario.rol() == Rol.ADMIN) {
            return admin(zona);
        }
        if (usuario.rol() == Rol.PSICORIENTADOR) {
            return psicorientador(usuario.id(), zona);
        }
        if (usuario.rol() == Rol.DOCENTE) {
            return docente(usuario.id());
        }
        return estudiante(usuario.id());
    }

    // ---------------------------------------------------------------- por rol

    private InicioResponse admin(String zona) {

        JsonNode conteos = leer(repository.conteosAdmin(zona));
        JsonNode estados = conteos.get("estados");
        JsonNode sinAtender = conteos.get("sinAtender");
        long activas = estados.get(0).asLong() + estados.get(1).asLong();

        List<TarjetaResponse> tarjetas = new ArrayList<>();
        tarjetas.add(new TarjetaResponse("ESTUDIANTES", "Estudiantes", conteos.get("estudiantes").asText(), "Activos", "/estudiantes"));
        tarjetas.add(new TarjetaResponse("DOCENTES", "Docentes", conteos.get("docentes").asText(), "Activos", "/personal"));
        tarjetas.add(new TarjetaResponse("PSICORIENTADORES", "Psicorientadores", conteos.get("psicorientadores").asText(),
                "Activos", "/personal"));
        tarjetas.add(new TarjetaResponse("ALERTAS", "Alertas activas", String.valueOf(activas),
                sinAtender.get(0).asText() + " estudiantes sin psicorientador", "/estadisticas"));

        return new InicioResponse(
                "ADMIN",
                tarjetas,
                estados(estados),
                ultimosMeses(conteos.get("porMes")),
                new ArrayList<>(),
                "Ultimas alertas",
                elementos(leer(repository.ultimasAlertasDelColegio()), true));
    }

    private InicioResponse psicorientador(Long usuarioId, String zona) {

        JsonNode conteos = leer(repository.conteosPsicorientador(usuarioId, zona));
        JsonNode bandeja = conteos.get("bandeja");

        List<TarjetaResponse> tarjetas = new ArrayList<>();
        tarjetas.add(new TarjetaResponse("BANDEJA", "En la bandeja", bandeja.get(0).asText(),
                bandeja.get(1).asText() + " prioritarios", "/atencion/bandeja"));
        tarjetas.add(new TarjetaResponse("CASOS", "Mis casos", conteos.get("misCasos").asText(), "Estudiantes que atiendo",
                "/atencion/estudiantes"));
        tarjetas.add(new TarjetaResponse("CITAS", "Citas de hoy", conteos.get("citasHoy").asText(), "Programadas", "/atencion/agenda"));
        if (!conteos.get("porValorar").isNull()) {
            tarjetas.add(new TarjetaResponse("VALORAR", "Por valorar", conteos.get("porValorar").asText(),
                    "Valoraciones de rutina", "/valoraciones"));
        }

        return new InicioResponse(
                "PSICORIENTADOR",
                tarjetas,
                estados(conteos.get("estados")),
                new ArrayList<>(),
                new ArrayList<>(),
                "Mis proximas citas",
                elementos(leer(repository.proximasCitas(usuarioId)), false));
    }

    private InicioResponse docente(Long usuarioId) {

        JsonNode conteos = leer(repository.conteosDocente(usuarioId));
        JsonNode estados = conteos.get("estados");
        long total = estados.get(0).asLong() + estados.get(1).asLong() + estados.get(2).asLong();

        List<TarjetaResponse> tarjetas = new ArrayList<>();
        tarjetas.add(new TarjetaResponse("ALERTAS", "Mis alertas", String.valueOf(total), "Las que he reportado", "/alertas"));
        tarjetas.add(new TarjetaResponse("PENDIENTES", "Sin atender", estados.get(0).asText(), "Esperando psicorientador", "/alertas"));
        tarjetas.add(new TarjetaResponse("EN_PROCESO", "En atencion", estados.get(1).asText(), "Con psicorientador", "/alertas"));
        tarjetas.add(new TarjetaResponse("COMPLETADAS", "Atendidas", estados.get(2).asText(), "Completadas", "/alertas"));

        Map<String, Long> niveles = new HashMap<>();
        for (JsonNode fila : conteos.get("porNivel")) {
            niveles.put(fila.get(0).asText(), fila.get(1).asLong());
        }
        List<ConteoResponse> porNivel = new ArrayList<>();
        for (String[] nivel : NIVELES) {
            long cantidad = 0;
            if (niveles.containsKey(nivel[0])) {
                cantidad = niveles.get(nivel[0]);
            }
            porNivel.add(new ConteoResponse(nivel[0], nivel[1], cantidad));
        }

        return new InicioResponse(
                "DOCENTE",
                tarjetas,
                estados(estados),
                new ArrayList<>(),
                porNivel,
                "Mis ultimas alertas",
                elementos(leer(repository.ultimasAlertasDe(usuarioId)), true));
    }

    private InicioResponse estudiante(Long usuarioId) {

        JsonNode datos = leer(repository.estudiante(usuarioId));
        JsonNode cita = datos.get("cita");
        JsonNode solicitudes = datos.get("solicitudes");

        List<TarjetaResponse> tarjetas = new ArrayList<>();
        if (cita.isNull()) {
            tarjetas.add(new TarjetaResponse("CITA", "Proxima cita", "Sin citas", "Orientacion te avisara cuando tengas una",
                    "/mi-proceso"));
        } else {
            OffsetDateTime inicio = OffsetDateTime.parse(cita.get(0).asText());
            String lugar = "Virtual";
            if (!"VIRTUAL".equals(cita.get(1).asText())) {
                lugar = "Presencial";
                if (!cita.get(2).isNull()) {
                    lugar = cita.get(2).asText();
                }
            }
            tarjetas.add(new TarjetaResponse("CITA", "Proxima cita", Fechas.cita(inicio), lugar, "/mi-proceso"));
        }
        JsonNode orientador = datos.get("orientador");
        if (orientador.isNull()) {
            tarjetas.add(new TarjetaResponse("ORIENTADOR", "Me acompana", "Sin asignar",
                    "Orientacion te asigna a alguien cuando lo necesites", "/mi-proceso"));
        } else {
            tarjetas.add(new TarjetaResponse("ORIENTADOR", "Me acompana", orientador.asText(), "De orientacion",
                    "/mi-proceso"));
        }
        tarjetas.add(new TarjetaResponse("SOLICITUDES", "Mis solicitudes de ayuda", solicitudes.get(0).asText(),
                solicitudes.get(1).asText() + " en curso", "/mi-proceso"));

        // su proceso con palabras amables (como en mi proceso), solo el estado
        JsonNode conteo = datos.get("estados");
        List<ConteoResponse> estados = new ArrayList<>();
        estados.add(new ConteoResponse("PENDIENTE", "Recibidas", conteo.get(0).asLong()));
        estados.add(new ConteoResponse("EN_PROCESO", "En atencion", conteo.get(1).asLong()));
        estados.add(new ConteoResponse("COMPLETADA", "Atendidas", conteo.get(2).asLong()));

        return new InicioResponse("ESTUDIANTE", tarjetas, estados, new ArrayList<>(), new ArrayList<>(),
                "Mis citas", elementos(datos.get("citas"), false));
    }

    // ---------------------------------------------------------------- ayudas

    private JsonNode leer(String json) {

        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("La base devolvio un json invalido en inicio", e);
        }
    }

    private List<ConteoResponse> estados(JsonNode estados) {

        List<ConteoResponse> respuesta = new ArrayList<>();
        respuesta.add(new ConteoResponse("PENDIENTE", "Pendientes", estados.get(0).asLong()));
        respuesta.add(new ConteoResponse("EN_PROCESO", "En proceso", estados.get(1).asLong()));
        respuesta.add(new ConteoResponse("COMPLETADA", "Completadas", estados.get(2).asLong()));
        return respuesta;
    }

    // los ultimos seis meses, tambien los que estan en 0
    private List<ConteoResponse> ultimosMeses(JsonNode filas) {

        Map<String, Long> totales = new HashMap<>();
        for (JsonNode fila : filas) {
            totales.put(fila.get(0).asText(), fila.get(1).asLong());
        }

        List<ConteoResponse> respuesta = new ArrayList<>();
        YearMonth mes = YearMonth.now().minusMonths(5);
        for (int i = 0; i < 6; i++) {
            long total = 0;
            if (totales.containsKey(mes.toString())) {
                total = totales.get(mes.toString());
            }
            respuesta.add(new ConteoResponse(mes.toString(), Fechas.mesCorto(mes), total));
            mes = mes.plusMonths(1);
        }
        return respuesta;
    }

    // alertas: [nombre, categoria, fecha, estado, nivel, enlace]. citas: [nombre, lugar, fecha, estado, enlace]
    private List<ElementoResponse> elementos(JsonNode filas, boolean sonAlertas) {

        List<ElementoResponse> respuesta = new ArrayList<>();
        for (JsonNode fila : filas) {
            Instant fecha = OffsetDateTime.parse(fila.get(2).asText()).toInstant();
            if (sonAlertas) {
                respuesta.add(new ElementoResponse(fila.get(0).asText(), fila.get(1).asText(), fecha,
                        fila.get(3).asText(), fila.get(4).asText(), fila.get(5).asText(),
                        fila.get(6).asText(), fila.get(7).asBoolean()));
            } else {
                respuesta.add(new ElementoResponse(fila.get(0).asText(), fila.get(1).asText(), fecha,
                        fila.get(3).asText(), null, fila.get(4).asText(), null, false));
            }
        }
        return respuesta;
    }
}
