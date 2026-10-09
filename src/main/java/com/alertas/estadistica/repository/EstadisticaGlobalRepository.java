package com.alertas.estadistica.repository;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.springframework.stereotype.Repository;

// estadisticas del superadmin. todo pasa por las funciones sa_* (V16), que solo devuelven totales,
// y cada peticion es UNA consulta: las funciones se juntan en un solo json
@Repository
public class EstadisticaGlobalRepository {

    // el colegio va por su slug (null = todos); asi no hace falta otra consulta para buscar su id
    private static final String INS = "(SELECT ins_id FROM instituciones WHERE ins_slug = CAST(:slug AS text))";
    private static final String FILTRO = INS + ", CAST(:desde AS date), CAST(:hasta AS date), CAST(:zona AS text)";

    private static final String COMPARATIVO = """
            (SELECT coalesce(json_agg(json_build_array(c.nombre, c.slug, c.activa, c.estudiantes, c.alertas, c.pendientes,
                                                       c.en_proceso, c.completadas, c.sms_enviados, c.sms_segmentos)
                                      ORDER BY c.orden), CAST('[]' AS json))
             FROM sa_comparativo(CAST(:desde AS date), CAST(:hasta AS date), CAST(:zona AS text),
                                 CAST(:limite AS integer), CAST(:saltar AS integer))
                  WITH ORDINALITY AS c(nombre, slug, activa, estudiantes, alertas, pendientes, en_proceso,
                                       completadas, sms_enviados, sms_segmentos, orden))
            """;

    private static final String TODO = """
            SELECT CAST(json_build_object(
                'existe', (CAST(:slug AS text) IS NULL OR %1$s IS NOT NULL),
                'resumen', (SELECT json_build_array(instituciones_activas, instituciones_inactivas, alertas,
                                                    sms_enviados, sms_fallidos, sms_segmentos)
                            FROM sa_resumen(%2$s)),
                'roles', (SELECT coalesce(json_agg(json_build_array(rol, total)), CAST('[]' AS json))
                          FROM sa_usuarios_por_rol(%1$s)),
                'porMes', (SELECT coalesce(json_agg(json_build_array(mes, total)), CAST('[]' AS json))
                           FROM sa_alertas_por_mes(%2$s)),
                'porCategoria', (SELECT coalesce(json_agg(json_build_array(categoria, total)), CAST('[]' AS json))
                                 FROM sa_alertas_por_categoria(%2$s)),
                'smsPorMes', (SELECT coalesce(json_agg(json_build_array(mes, enviados, fallidos, segmentos)), CAST('[]' AS json))
                              FROM sa_sms_por_mes(%2$s)),
                'comparativo', %3$s,
                'instituciones', (SELECT count(*) FROM instituciones)
            ) AS text)
            """.formatted(INS, FILTRO, COMPARATIVO);

    private static final String PAGINA_COMPARATIVO = """
            SELECT CAST(json_build_object(
                'comparativo', %s,
                'instituciones', (SELECT count(*) FROM instituciones)
            ) AS text)
            """.formatted(COMPARATIVO);

    private final EntityManager em;

    public EstadisticaGlobalRepository(EntityManager em) {
        this.em = em;
    }

    // todo el tablero en un json, con la primera pagina del comparativo
    public String todo(String slug, LocalDate desde, LocalDate hasta, String zona, int limite, int saltar) {
        return (String) em.createNativeQuery(TODO)
                .setParameter("slug", slug)
                .setParameter("desde", fecha(desde))
                .setParameter("hasta", fecha(hasta))
                .setParameter("zona", zona)
                .setParameter("limite", limite)
                .setParameter("saltar", saltar)
                .getSingleResult();
    }

    // otra pagina del comparativo con el total de colegios
    public String paginaComparativo(LocalDate desde, LocalDate hasta, String zona, int limite, int saltar) {
        return (String) em.createNativeQuery(PAGINA_COMPARATIVO)
                .setParameter("desde", fecha(desde))
                .setParameter("hasta", fecha(hasta))
                .setParameter("zona", zona)
                .setParameter("limite", limite)
                .setParameter("saltar", saltar)
                .getSingleResult();
    }

    // como texto: asi el null llega con tipo y el CAST lo vuelve fecha
    private String fecha(LocalDate fecha) {

        if (fecha == null) {
            return null;
        }
        return fecha.toString();
    }
}
