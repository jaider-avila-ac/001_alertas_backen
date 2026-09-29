package com.alertas.estadistica.service.serviceImpl;

import com.alertas.estadistica.dto.ConteoResponse;
import com.alertas.estadistica.dto.EstadisticasResponse;
import com.alertas.estadistica.dto.FiltroEstadistica;
import com.alertas.estadistica.dto.IndicadoresResponse;
import com.alertas.estadistica.dto.PsicorientadorConteoResponse;
import com.alertas.estadistica.repository.EstadisticaRepository;
import com.alertas.estadistica.service.EstadisticaService;
import com.alertas.shared.Fechas;
import com.alertas.shared.TenantSupport;
import com.alertas.shared.excel.ArchivoExcel;
import com.alertas.shared.excel.HojaExcel;
import com.alertas.shared.exception.ApiException;
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
    private final EntityManager em;

    public EstadisticaServiceImpl(EstadisticaRepository repository, EntityManager em) {
        this.repository = repository;
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public EstadisticasResponse resumen(FiltroEstadistica filtro) {

        TenantSupport.requireTenant(em);
        validar(filtro);

        String zona = ZoneId.systemDefault().getId();

        Object[] estados = repository.estados(filtro, zona);
        Object[] primeraCita = repository.primeraCita(filtro, zona);
        Object[] citas = repository.citas(filtro, zona);
        Object[] valoraciones = repository.valoraciones(filtro, zona);

        Double horas = null;
        if (primeraCita[0] != null) {
            horas = ((Number) primeraCita[0]).doubleValue();
        }

        IndicadoresResponse indicadores = new IndicadoresResponse(
                numero(estados[0]),
                numero(estados[1]),
                numero(estados[2]),
                numero(estados[3]),
                numero(estados[4]),
                numero(estados[5]),
                numero(citas[0]),
                numero(citas[1]),
                numero(citas[2]),
                numero(citas[3]),
                horas,
                numero(primeraCita[1]),
                numero(valoraciones[0]),
                numero(valoraciones[1]));

        List<ConteoResponse> porCategoria = new ArrayList<>();
        for (Object[] fila : repository.porCategoria(filtro, zona)) {
            porCategoria.add(new ConteoResponse((String) fila[0], (String) fila[0], numero(fila[1])));
        }

        List<ConteoResponse> porGrupo = new ArrayList<>();
        for (Object[] fila : repository.porGrupo(filtro, zona)) {
            String etiqueta = fila[0] + " " + fila[1];
            // sin filtro de anio pueden salir grupos de varios anios
            if (filtro.anioId() == null) {
                etiqueta = etiqueta + " " + fila[2];
            }
            porGrupo.add(new ConteoResponse(etiqueta, etiqueta, numero(fila[3])));
        }

        List<PsicorientadorConteoResponse> porPsicorientador = new ArrayList<>();
        for (Object[] fila : repository.porPsicorientador(filtro, zona)) {
            porPsicorientador.add(new PsicorientadorConteoResponse((String) fila[0], numero(fila[1]), numero(fila[2])));
        }

        // los rangos siempre (tambien en 0) y al final los que no tienen fecha, solo si hay
        List<Object[]> filasEdad = repository.porEdad(filtro, zona);
        List<ConteoResponse> porEdad = enOrden(filasEdad, EDADES, true);
        porEdad.addAll(enOrden(filasEdad, SIN_EDAD, false));

        return new EstadisticasResponse(
                indicadores,
                porMes(repository.porMes(filtro, zona)),
                porCategoria,
                enOrden(repository.porNivel(filtro, zona), NIVELES, true),
                porGrupo,
                enOrden(repository.porGenero(filtro, zona), GENEROS, false),
                porEdad,
                enOrden(repository.porOrigen(filtro, zona), ORIGENES, false),
                porPsicorientador);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] excel(FiltroEstadistica filtro) {

        EstadisticasResponse resumen = resumen(filtro);
        IndicadoresResponse ind = resumen.indicadores();

        List<Object[]> filtros = new ArrayList<>();
        filtros.add(new Object[] {"Anio lectivo", textoFiltro(repository.nombreAnio(filtro.anioId()))});
        filtros.add(new Object[] {"Desde", textoFiltro(filtro.desde())});
        filtros.add(new Object[] {"Hasta", textoFiltro(filtro.hasta())});
        filtros.add(new Object[] {"Grado", textoFiltro(repository.nombreGrado(filtro.gradoId()))});
        filtros.add(new Object[] {"Grupo", textoFiltro(repository.nombreGrupo(filtro.grupoId()))});
        filtros.add(new Object[] {"Categoria", textoFiltro(repository.nombreCategoria(filtro.categoriaId()))});
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
        hojas.add(hoja("Por edad", "Edad (anios)", resumen.porEdad()));
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

    // todos los meses entre el primero y el ultimo, los que no tienen alertas en 0
    private List<ConteoResponse> porMes(List<Object[]> filas) {

        List<ConteoResponse> respuesta = new ArrayList<>();
        if (filas.isEmpty()) {
            return respuesta;
        }

        Map<String, Long> totales = new HashMap<>();
        for (Object[] fila : filas) {
            totales.put((String) fila[0], numero(fila[1]));
        }

        YearMonth mes = YearMonth.parse((String) filas.get(0)[0]);
        YearMonth ultimo = YearMonth.parse((String) filas.get(filas.size() - 1)[0]);

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
    private List<ConteoResponse> enOrden(List<Object[]> filas, String[][] orden, boolean conCeros) {

        Map<String, Long> totales = new HashMap<>();
        for (Object[] fila : filas) {
            totales.put((String) fila[0], numero(fila[1]));
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

    private String textoFiltro(Object valor) {

        if (valor == null) {
            return "Todos";
        }
        return String.valueOf(valor);
    }

    private long numero(Object valor) {

        if (valor == null) {
            return 0;
        }
        return ((Number) valor).longValue();
    }
}
