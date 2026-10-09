package com.alertas.estadistica.service.serviceImpl;

import com.alertas.estadistica.dto.ComparativoResponse;
import com.alertas.estadistica.dto.ConteoResponse;
import com.alertas.estadistica.dto.EstadisticasGlobalesResponse;
import com.alertas.estadistica.dto.ResumenGlobalResponse;
import com.alertas.estadistica.dto.SmsMesResponse;
import com.alertas.estadistica.repository.EstadisticaGlobalRepository;
import com.alertas.estadistica.service.EstadisticaGlobalService;
import com.alertas.shared.Fechas;
import com.alertas.shared.dto.PageResponse;
import com.alertas.shared.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstadisticaGlobalServiceImpl implements EstadisticaGlobalService {

    private static final int TAMANIO_MAXIMO = 50;
    private static final int TAMANIO_COMPARATIVO = 20;

    private static final String[][] ROLES = {
        {"ADMIN", "Administradores"}, {"PSICORIENTADOR", "Psicorientadores"}, {"DOCENTE", "Docentes"},
        {"ESTUDIANTE", "Estudiantes"}};

    private final EstadisticaGlobalRepository repository;
    private final ObjectMapper mapper;

    public EstadisticaGlobalServiceImpl(EstadisticaGlobalRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    // una sola consulta: todas las funciones sa_* juntas, con la primera pagina del comparativo
    @Override
    @Transactional(readOnly = true)
    public EstadisticasGlobalesResponse resumen(String slug, LocalDate desde, LocalDate hasta) {

        validar(desde, hasta);

        String colegio = null;
        if (slug != null && !slug.isBlank()) {
            colegio = slug;
        }

        String zona = ZoneId.systemDefault().getId();
        PageRequest primeraPagina = PageRequest.of(0, TAMANIO_COMPARATIVO);
        JsonNode todo = leer(repository.todo(colegio, desde, hasta, zona, TAMANIO_COMPARATIVO, 0));

        if (!todo.get("existe").asBoolean()) {
            throw ApiException.noEncontrado("La institucion no existe");
        }

        JsonNode fila = todo.get("resumen");
        ResumenGlobalResponse resumen = new ResumenGlobalResponse(
                fila.get(0).asLong(), fila.get(1).asLong(), fila.get(2).asLong(),
                fila.get(3).asLong(), fila.get(4).asLong(), fila.get(5).asLong());

        // roles en orden fijo, tambien los que estan en 0
        Map<String, Long> porRol = new HashMap<>();
        for (JsonNode rol : todo.get("roles")) {
            porRol.put(rol.get(0).asText(), rol.get(1).asLong());
        }
        List<ConteoResponse> usuarios = new ArrayList<>();
        for (String[] rol : ROLES) {
            long total = 0;
            if (porRol.containsKey(rol[0])) {
                total = porRol.get(rol[0]);
            }
            usuarios.add(new ConteoResponse(rol[0], rol[1], total));
        }

        List<ConteoResponse> porCategoria = new ArrayList<>();
        for (JsonNode categoria : todo.get("porCategoria")) {
            porCategoria.add(new ConteoResponse(categoria.get(0).asText(), categoria.get(0).asText(), categoria.get(1).asLong()));
        }

        return new EstadisticasGlobalesResponse(
                resumen,
                usuarios,
                alertasPorMes(todo.get("porMes")),
                porCategoria,
                smsPorMes(todo.get("smsPorMes")),
                pagina(todo, primeraPagina));
    }

    // cambiar de pagina en el comparativo: otra sola consulta
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ComparativoResponse> comparativo(LocalDate desde, LocalDate hasta, int pagina, int tamanio) {

        validar(desde, hasta);

        PageRequest pageRequest = PageRequest.of(Math.max(pagina, 0), limitarTamanio(tamanio));
        String zona = ZoneId.systemDefault().getId();
        JsonNode datos = leer(repository.paginaComparativo(
                desde, hasta, zona, pageRequest.getPageSize(), (int) pageRequest.getOffset()));

        return pagina(datos, pageRequest);
    }

    // ---------------------------------------------------------------- ayudas

    private PageResponse<ComparativoResponse> pagina(JsonNode datos, PageRequest pageRequest) {

        List<ComparativoResponse> contenido = new ArrayList<>();
        for (JsonNode fila : datos.get("comparativo")) {
            contenido.add(new ComparativoResponse(
                    fila.get(0).asText(),
                    fila.get(1).asText(),
                    fila.get(2).asBoolean(),
                    fila.get(3).asLong(),
                    fila.get(4).asLong(),
                    fila.get(5).asLong(),
                    fila.get(6).asLong(),
                    fila.get(7).asLong(),
                    fila.get(8).asLong(),
                    fila.get(9).asLong()));
        }

        long total = datos.get("instituciones").asLong();
        return PageResponse.de(contenido, new PageImpl<>(contenido, pageRequest, total));
    }

    private JsonNode leer(String json) {

        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("La base devolvio un json invalido en estadisticas globales", e);
        }
    }

    private void validar(LocalDate desde, LocalDate hasta) {

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw ApiException.invalido("La fecha inicial no puede ser despues de la final");
        }
    }

    // todos los meses entre el primero y el ultimo; los que no tienen alertas en 0
    private List<ConteoResponse> alertasPorMes(JsonNode filas) {

        List<ConteoResponse> respuesta = new ArrayList<>();
        if (filas.isEmpty()) {
            return respuesta;
        }

        Map<String, Long> totales = new HashMap<>();
        String primero = null;
        String ultimo = null;
        for (JsonNode fila : filas) {
            String mes = fila.get(0).asText();
            totales.put(mes, fila.get(1).asLong());
            if (primero == null || mes.compareTo(primero) < 0) {
                primero = mes;
            }
            if (ultimo == null || mes.compareTo(ultimo) > 0) {
                ultimo = mes;
            }
        }

        YearMonth mes = YearMonth.parse(primero);
        YearMonth fin = YearMonth.parse(ultimo);
        while (!mes.isAfter(fin)) {
            long total = 0;
            if (totales.containsKey(mes.toString())) {
                total = totales.get(mes.toString());
            }
            respuesta.add(new ConteoResponse(mes.toString(), Fechas.mesCorto(mes), total));
            mes = mes.plusMonths(1);
        }
        return respuesta;
    }

    private List<SmsMesResponse> smsPorMes(JsonNode filas) {

        List<SmsMesResponse> respuesta = new ArrayList<>();
        if (filas.isEmpty()) {
            return respuesta;
        }

        Map<String, JsonNode> porMes = new HashMap<>();
        String primero = null;
        String ultimo = null;
        for (JsonNode fila : filas) {
            String mes = fila.get(0).asText();
            porMes.put(mes, fila);
            if (primero == null || mes.compareTo(primero) < 0) {
                primero = mes;
            }
            if (ultimo == null || mes.compareTo(ultimo) > 0) {
                ultimo = mes;
            }
        }

        YearMonth mes = YearMonth.parse(primero);
        YearMonth fin = YearMonth.parse(ultimo);
        while (!mes.isAfter(fin)) {
            JsonNode fila = porMes.get(mes.toString());
            if (fila == null) {
                respuesta.add(new SmsMesResponse(mes.toString(), Fechas.mesCorto(mes), 0, 0, 0));
            } else {
                respuesta.add(new SmsMesResponse(mes.toString(), Fechas.mesCorto(mes),
                        fila.get(1).asLong(), fila.get(2).asLong(), fila.get(3).asLong()));
            }
            mes = mes.plusMonths(1);
        }
        return respuesta;
    }

    private int limitarTamanio(int tamanio) {

        if (tamanio < 1) {
            return 20;
        }

        if (tamanio > TAMANIO_MAXIMO) {
            return TAMANIO_MAXIMO;
        }

        return tamanio;
    }
}
