package com.alertas.estadistica.service.serviceImpl;

import com.alertas.estadistica.dto.ConteoResponse;
import com.alertas.estadistica.dto.EstadisticasResponse;
import com.alertas.estadistica.dto.FiltroEstadistica;
import com.alertas.estadistica.dto.FiltrosDisponiblesResponse;
import com.alertas.estadistica.dto.IndicadoresResponse;
import com.alertas.estadistica.dto.PsicorientadorConteoResponse;
import com.alertas.estadistica.repository.EstadisticaRepository;
import com.alertas.estadistica.service.EstadisticaService;
import com.alertas.shared.Fechas;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.excel.ArchivoExcel;
import com.alertas.shared.excel.HojaExcel;
import com.alertas.shared.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstadisticaServiceImpl implements EstadisticaService {

    // orden fijo en los graficos
    private static final String[][] NIVELES = {
        {"LEVE", "Leve"}, {"MODERADO", "Moderado"}, {"ALTO", "Alto"}, {"CRITICO", "Critico"}};
    private static final String[][] GENEROS = {
        {"F", "Femenino"}, {"M", "Masculino"}, {"O", "Otro"}, {"N", "Sin dato"}};
    // edad que tenia el estudiante cuando se creo la alerta
    private static final String[][] EDADES = {
        {"HASTA_8", "8 o menos"}, {"DE_9_A_11", "9 a 11"}, {"DE_12_A_14", "12 a 14"}, {"DE_15_A_17", "15 a 17"},
        {"DESDE_18", "18 o mas"}};
    private static final String[][] SIN_EDAD = {{"N", "Sin fecha de nacimiento"}};
    private static final String[][] ORIGENES = {
        {"DOCENTE", "Docente"}, {"PSICORIENTADOR", "Psicorientador"}, {"ADMIN", "Administrador"},
        {"ESTUDIANTE", "Estudiante (pidio ayuda)"}};

    private final EstadisticaRepository repository;
    private final ObjectMapper mapper;
    private final EntityManager em;

    public EstadisticaServiceImpl(EstadisticaRepository repository, ObjectMapper mapper, EntityManager em) {
        this.repository = repository;
        this.mapper = mapper;
        this.em = em;
    }

    // una consulta para fijar el colegio (RLS) y dos de datos
    @Override
    @Transactional(readOnly = true)
    public EstadisticasResponse resumen(FiltroEstadistica filtro) {

        TenantSupport.requireTenant(em);
        validar(filtro);

        String zona = ZoneId.systemDefault().getId();
        JsonNode alertas = leer(repository.alertas(filtro, zona));
        JsonNode otros = leer(repository.citasYFiltros(filtro, zona));

        JsonNode estados = alertas.get("estados");
        JsonNode primeraCita = alertas.get("primeraCita");
        JsonNode citas = otros.get("citas");
        JsonNode valoraciones = otros.get("valoraciones");

        Double horas = null;
        if (!primeraCita.get(0).isNull()) {
            horas = primeraCita.get(0).asDouble();
        }

        IndicadoresResponse indicadores = new IndicadoresResponse(
                estados.get(0).asLong(),
                estados.get(1).asLong(),
                estados.get(2).asLong(),
                estados.get(3).asLong(),
                estados.get(4).asLong(),
                estados.get(5).asLong(),
                citas.get(0).asLong(),
                citas.get(1).asLong(),
                citas.get(2).asLong(),
                citas.get(3).asLong(),
                horas,
                primeraCita.get(1).asLong(),
                valoraciones.get(0).asLong(),
                valoraciones.get(1).asLong());

        List<ConteoResponse> porCategoria = new ArrayList<>();
        for (JsonNode fila : alertas.get("porCategoria")) {
            porCategoria.add(new ConteoResponse(fila.get(0).asText(), fila.get(0).asText(), fila.get(1).asLong()));
        }

        List<ConteoResponse> porGrupo = new ArrayList<>();
        for (JsonNode fila : alertas.get("porGrupo")) {
            String etiqueta = fila.get(0).asText() + " " + fila.get(1).asText();
            // viendo todos los anios pueden salir grupos de varios
            if (filtro.todosLosAnios()) {
                etiqueta = etiqueta + " " + fila.get(2).asInt();
            }
            porGrupo.add(new ConteoResponse(etiqueta, etiqueta, fila.get(3).asLong()));
        }

        List<PsicorientadorConteoResponse> porPsicorientador = new ArrayList<>();
        for (JsonNode fila : alertas.get("porPsicorientador")) {
            porPsicorientador.add(new PsicorientadorConteoResponse(
                    fila.get(0).asText(), fila.get(1).asLong(), fila.get(2).asLong()));
        }

        // los rangos de edad siempre (tambien en 0) y al final los que no tienen fecha, solo si hay
        List<ConteoResponse> porEdad = enOrden(alertas.get("porEdad"), EDADES, true);
        porEdad.addAll(enOrden(alertas.get("porEdad"), SIN_EDAD, false));

        Long anioAplicado = null;
        if (!otros.get("anioAplicado").isNull()) {
            anioAplicado = otros.get("anioAplicado").asLong();
        }

        return new EstadisticasResponse(
                anioAplicado,
                indicadores,
                porMes(alertas.get("porMes")),
                porCategoria,
                enOrden(alertas.get("porNivel"), NIVELES, true),
                porGrupo,
                enOrden(alertas.get("porGenero"), GENEROS, false),
                porEdad,
                enOrden(alertas.get("porOrigen"), ORIGENES, false),
                porPsicorientador,
                filtros(otros));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] excel(FiltroEstadistica filtro) {

        EstadisticasResponse resumen = resumen(filtro);
        IndicadoresResponse ind = resumen.indicadores();
        FiltrosDisponiblesResponse opciones = resumen.filtros();

        // los nombres de los filtros salen de las mismas listas: sin consultas de mas
        String anio = "Todos";
        for (FiltrosDisponiblesResponse.Anio opcion : opciones.anios()) {
            if (opcion.id().equals(resumen.anioId())) {
                anio = String.valueOf(opcion.anio());
            }
        }

        List<Object[]> filtros = new ArrayList<>();
        filtros.add(new Object[] {"Año lectivo", anio});
        filtros.add(new Object[] {"Desde", textoFiltro(filtro.desde())});
        filtros.add(new Object[] {"Hasta", textoFiltro(filtro.hasta())});
        filtros.add(new Object[] {"Grado", nombreDe(opciones.grados(), filtro.gradoId())});
        filtros.add(new Object[] {"Grupo", nombreDeGrupo(opciones, filtro.grupoId())});
        filtros.add(new Object[] {"Categoria", nombreDe(opciones.categorias(), filtro.categoriaId())});
        filtros.add(new Object[] {"", ""});
        filtros.add(new Object[] {"Alertas", ind.alertas()});
        filtros.add(new Object[] {"Pendientes", ind.pendientes()});
        filtros.add(new Object[] {"En proceso", ind.enProceso()});
        filtros.add(new Object[] {"Completadas", ind.completadas()});
        filtros.add(new Object[] {"Prioritarias", ind.prioritarias()});
        filtros.add(new Object[] {"Estudiantes con alertas", ind.estudiantes()});
        filtros.add(new Object[] {"Citas realizadas", ind.citasRealizadas()});
        filtros.add(new Object[] {"Inasistencias", ind.citasNoAsistio()});
        filtros.add(new Object[] {"Citas canceladas", ind.citasCanceladas()});
        filtros.add(new Object[] {"Citas programadas", ind.citasProgramadas()});
        if (ind.horasPrimeraCita() == null) {
            filtros.add(new Object[] {"Dias promedio hasta la primera cita", "Sin citas"});
        } else {
            filtros.add(new Object[] {"Dias promedio hasta la primera cita", Math.round(ind.horasPrimeraCita() / 24.0 * 10) / 10.0});
        }
        filtros.add(new Object[] {"Valoraciones de rutina", ind.valoraciones()});
        filtros.add(new Object[] {"Estudiantes valorados", ind.estudiantesValorados()});

        List<HojaExcel> hojas = new ArrayList<>();
        hojas.add(new HojaExcel("Resumen", new String[] {"Dato", "Valor"}, filtros));
        hojas.add(hoja("Por mes", "Mes", resumen.porMes()));
        hojas.add(hoja("Por categoria", "Categoria", resumen.porCategoria()));
        hojas.add(hoja("Por nivel", "Nivel", resumen.porNivel()));
        hojas.add(hoja("Por grado y grupo", "Grupo", resumen.porGrupo()));
        hojas.add(hoja("Por genero", "Genero", resumen.porGenero()));
        hojas.add(hoja("Por edad", "Edad (años)", resumen.porEdad()));
        hojas.add(hoja("Por origen", "Quien la creo", resumen.porOrigen()));

        List<Object[]> psicorientadores = new ArrayList<>();
        for (PsicorientadorConteoResponse fila : resumen.porPsicorientador()) {
            psicorientadores.add(new Object[] {fila.nombre(), fila.atendidas(), fila.completadas()});
        }
        hojas.add(new HojaExcel("Por psicorientador",
                new String[] {"Psicorientador", "Alertas atendidas", "Completadas"}, psicorientadores));

        return ArchivoExcel.reporte(hojas);
    }

    // ---------------------------------------------------------------- ayudas

    private void validar(FiltroEstadistica filtro) {

        if (filtro.desde() != null && filtro.hasta() != null && filtro.desde().isAfter(filtro.hasta())) {
            throw ApiException.invalido("La fecha inicial no puede ser despues de la final");
        }
    }

    private JsonNode leer(String json) {

        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("La base devolvio un json invalido en estadisticas", e);
        }
    }

    private FiltrosDisponiblesResponse filtros(JsonNode otros) {

        List<FiltrosDisponiblesResponse.Anio> anios = new ArrayList<>();
        for (JsonNode fila : otros.get("anios")) {
            anios.add(new FiltrosDisponiblesResponse.Anio(fila.get(0).asLong(), fila.get(1).asInt(), fila.get(2).asBoolean()));
        }

        List<FiltrosDisponiblesResponse.Opcion> grados = new ArrayList<>();
        for (JsonNode fila : otros.get("grados")) {
            grados.add(new FiltrosDisponiblesResponse.Opcion(fila.get(0).asLong(), fila.get(1).asText()));
        }

        List<FiltrosDisponiblesResponse.Grupo> grupos = new ArrayList<>();
        for (JsonNode fila : otros.get("grupos")) {
            grupos.add(new FiltrosDisponiblesResponse.Grupo(fila.get(0).asLong(), fila.get(1).asText(), fila.get(2).asLong()));
        }

        List<FiltrosDisponiblesResponse.Opcion> categorias = new ArrayList<>();
        for (JsonNode fila : otros.get("categorias")) {
            categorias.add(new FiltrosDisponiblesResponse.Opcion(fila.get(0).asLong(), fila.get(1).asText()));
        }

        return new FiltrosDisponiblesResponse(anios, grados, grupos, categorias);
    }

    // todos los meses entre el primero y el ultimo, los que no tienen alertas en 0
    private List<ConteoResponse> porMes(JsonNode filas) {

        List<ConteoResponse> respuesta = new ArrayList<>();
        if (filas.isEmpty()) {
            return respuesta;
        }

        Map<String, Long> totales = new HashMap<>();
        for (JsonNode fila : filas) {
            totales.put(fila.get(0).asText(), fila.get(1).asLong());
        }

        YearMonth mes = YearMonth.parse(filas.get(0).get(0).asText());
        YearMonth ultimo = YearMonth.parse(filas.get(filas.size() - 1).get(0).asText());

        while (!mes.isAfter(ultimo)) {
            String clave = mes.toString();
            long total = 0;
            if (totales.containsKey(clave)) {
                total = totales.get(clave);
            }
            respuesta.add(new ConteoResponse(clave, Fechas.mesCorto(mes), total));
            mes = mes.plusMonths(1);
        }

        return respuesta;
    }

    // en el orden fijo; conCeros: tambien los que no tienen alertas
    private List<ConteoResponse> enOrden(JsonNode filas, String[][] orden, boolean conCeros) {

        Map<String, Long> totales = new HashMap<>();
        for (JsonNode fila : filas) {
            totales.put(fila.get(0).asText(), fila.get(1).asLong());
        }

        List<ConteoResponse> respuesta = new ArrayList<>();
        for (String[] opcion : orden) {
            long total = 0;
            if (totales.containsKey(opcion[0])) {
                total = totales.get(opcion[0]);
            }
            if (total > 0 || conCeros) {
                respuesta.add(new ConteoResponse(opcion[0], opcion[1], total));
            }
        }
        return respuesta;
    }

    private HojaExcel hoja(String nombre, String columna, List<ConteoResponse> conteos) {

        List<Object[]> filas = new ArrayList<>();
        for (ConteoResponse conteo : conteos) {
            filas.add(new Object[] {conteo.etiqueta(), conteo.total()});
        }
        return new HojaExcel(nombre, new String[] {columna, "Alertas"}, filas);
    }

    private String nombreDe(List<FiltrosDisponiblesResponse.Opcion> opciones, Long id) {

        if (id == null) {
            return "Todos";
        }
        for (FiltrosDisponiblesResponse.Opcion opcion : opciones) {
            if (opcion.id().equals(id)) {
                return opcion.nombre();
            }
        }
        return "Todos";
    }

    private String nombreDeGrupo(FiltrosDisponiblesResponse opciones, Long id) {

        if (id == null) {
            return "Todos";
        }
        for (FiltrosDisponiblesResponse.Grupo grupo : opciones.grupos()) {
            if (grupo.id().equals(id)) {
                return nombreDe(opciones.grados(), grupo.gradoId()) + " " + grupo.nombre();
            }
        }
        return "Todos";
    }

    private String textoFiltro(Object valor) {

        if (valor == null) {
            return "Todos";
        }
        return String.valueOf(valor);
    }
}
