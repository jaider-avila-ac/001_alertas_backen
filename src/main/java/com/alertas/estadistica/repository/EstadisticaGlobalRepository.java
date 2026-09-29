package com.alertas.estadistica.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

// estadisticas del superadmin. todo pasa por las funciones sa_* (V16), que solo devuelven totales.
// institucionId null = todos los colegios
@Repository
public class EstadisticaGlobalRepository {

    // los cuatro parametros que reciben casi todas las funciones
    private static final String FILTRO =
            "CAST(:ins AS bigint), CAST(:desde AS date), CAST(:hasta AS date), CAST(:zona AS text)";

    private final EntityManager em;

    public EstadisticaGlobalRepository(EntityManager em) {
        this.em = em;
    }

    // [colegios activos, inactivos, alertas, sms enviados, fallidos, segmentos]
    public Object[] resumen(Long institucionId, LocalDate desde, LocalDate hasta, String zona) {
        return (Object[]) conFiltro("SELECT * FROM sa_resumen(" + FILTRO + ")", institucionId, desde, hasta, zona)
                .getSingleResult();
    }

    // [rol, total]
    @SuppressWarnings("unchecked")
    public List<Object[]> usuariosPorRol(Long institucionId) {
        return em.createNativeQuery("SELECT * FROM sa_usuarios_por_rol(CAST(:ins AS bigint))")
                .setParameter("ins", institucionId)
                .getResultList();
    }

    // [yyyy-mm, total]
    @SuppressWarnings("unchecked")
    public List<Object[]> alertasPorMes(Long institucionId, LocalDate desde, LocalDate hasta, String zona) {
        return conFiltro("SELECT * FROM sa_alertas_por_mes(" + FILTRO + ")", institucionId, desde, hasta, zona)
                .getResultList();
    }

    // [categoria, total]
    @SuppressWarnings("unchecked")
    public List<Object[]> alertasPorCategoria(Long institucionId, LocalDate desde, LocalDate hasta, String zona) {
        return conFiltro("SELECT * FROM sa_alertas_por_categoria(" + FILTRO + ")", institucionId, desde, hasta, zona)
                .getResultList();
    }

    // [yyyy-mm, enviados, fallidos, segmentos]
    @SuppressWarnings("unchecked")
    public List<Object[]> smsPorMes(Long institucionId, LocalDate desde, LocalDate hasta, String zona) {
        return conFiltro("SELECT * FROM sa_sms_por_mes(" + FILTRO + ")", institucionId, desde, hasta, zona)
                .getResultList();
    }

    // [nombre, slug, activa, estudiantes, alertas, pendientes, en proceso, completadas, sms enviados, segmentos]
    @SuppressWarnings("unchecked")
    public List<Object[]> comparativo(LocalDate desde, LocalDate hasta, String zona, int limite, int saltar) {
        return em.createNativeQuery("SELECT * FROM sa_comparativo(CAST(:desde AS date), CAST(:hasta AS date), "
                        + "CAST(:zona AS text), CAST(:limite AS integer), CAST(:saltar AS integer))")
                .setParameter("desde", fecha(desde))
                .setParameter("hasta", fecha(hasta))
                .setParameter("zona", zona)
                .setParameter("limite", limite)
                .setParameter("saltar", saltar)
                .getResultList();
    }

    // ---------------------------------------------------------------- ayudas

    private Query conFiltro(String sql, Long institucionId, LocalDate desde, LocalDate hasta, String zona) {

        return em.createNativeQuery(sql)
                .setParameter("ins", institucionId)
                .setParameter("desde", fecha(desde))
                .setParameter("hasta", fecha(hasta))
                .setParameter("zona", zona);
    }

    // como texto: asi el null llega con tipo y el CAST lo vuelve fecha
    private String fecha(LocalDate fecha) {

        if (fecha == null) {
            return null;
        }
        return fecha.toString();
    }
}
