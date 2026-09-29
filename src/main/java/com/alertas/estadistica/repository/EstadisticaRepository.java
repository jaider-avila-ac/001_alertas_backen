package com.alertas.estadistica.repository;

import com.alertas.estadistica.dto.FiltroEstadistica;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Repository;

// solo conteos (COUNT / GROUP BY) en la base, nunca se cargan entidades. RLS ya filtra por institucion.
// las fechas se comparan en la zona del colegio para que "hoy" sea el dia de alla y no el de utc
@Repository
public class EstadisticaRepository {

    // alertas: el anio y el grupo son los del momento en que se creo (no cambian si el estudiante cambia de grupo)
    private static final String ALERTAS = """
            FROM alertas a
            JOIN grupos g ON g.grp_id = a.ale_grp_id
            """;

    private static final String FILTRO_ALERTAS = """
            WHERE (CAST(:anioId AS bigint) IS NULL OR a.ale_anl_id = CAST(:anioId AS bigint))
              AND (CAST(:desde AS date) IS NULL
                   OR CAST(a.ale_creado_en AT TIME ZONE CAST(:zona AS text) AS date) >= CAST(:desde AS date))
              AND (CAST(:hasta AS date) IS NULL
                   OR CAST(a.ale_creado_en AT TIME ZONE CAST(:zona AS text) AS date) <= CAST(:hasta AS date))
              AND (CAST(:gradoId AS bigint) IS NULL OR g.grp_gra_id = CAST(:gradoId AS bigint))
              AND (CAST(:grupoId AS bigint) IS NULL OR g.grp_id = CAST(:grupoId AS bigint))
              AND (CAST(:categoriaId AS bigint) IS NULL OR a.ale_cat_id = CAST(:categoriaId AS bigint))
            """;

    private final EntityManager em;

    public EstadisticaRepository(EntityManager em) {
        this.em = em;
    }

    // [total, pendientes, en proceso, completadas, prioritarias, estudiantes distintos]
    public Object[] estados(FiltroEstadistica filtro, String zona) {

        String sql = """
                SELECT count(*),
                       count(*) FILTER (WHERE a.ale_estado = 'PENDIENTE'),
                       count(*) FILTER (WHERE a.ale_estado = 'EN_PROCESO'),
                       count(*) FILTER (WHERE a.ale_estado = 'COMPLETADA'),
                       count(*) FILTER (WHERE a.ale_prioritaria),
                       count(DISTINCT a.ale_est_id)
                """ + ALERTAS + FILTRO_ALERTAS;

        return (Object[]) consulta(sql, filtro, zona).getSingleResult();
    }

    // [promedio de horas hasta la primera cita, alertas que ya tuvieron cita]. las citas canceladas no cuentan
    public Object[] primeraCita(FiltroEstadistica filtro, String zona) {

        String sql = """
                SELECT avg(GREATEST(EXTRACT(EPOCH FROM (pc.primera - a.ale_creado_en)), 0) / 3600.0),
                       count(*)
                """ + ALERTAS + """
                JOIN LATERAL (
                    SELECT min(c.cit_inicio) AS primera
                    FROM citas_alertas ca
                    JOIN citas c ON c.cit_id = ca.cia_cit_id
                    WHERE ca.cia_ale_id = a.ale_id AND c.cit_estado <> 'CANCELADA'
                ) pc ON pc.primera IS NOT NULL
                """ + FILTRO_ALERTAS;

        return (Object[]) consulta(sql, filtro, zona).getSingleResult();
    }

    // [realizadas, no asistio, canceladas, programadas]. la categoria filtra por las alertas que se vieron en la cita
    public Object[] citas(FiltroEstadistica filtro, String zona) {

        String sql = """
                SELECT count(*) FILTER (WHERE c.cit_estado = 'REALIZADA'),
                       count(*) FILTER (WHERE c.cit_estado = 'NO_ASISTIO'),
                       count(*) FILTER (WHERE c.cit_estado = 'CANCELADA'),
                       count(*) FILTER (WHERE c.cit_estado = 'PROGRAMADA')
                FROM citas c
                JOIN grupos g ON g.grp_id = c.cit_grp_id
                WHERE (CAST(:anioId AS bigint) IS NULL OR g.grp_anl_id = CAST(:anioId AS bigint))
                  AND (CAST(:desde AS date) IS NULL
                       OR CAST(c.cit_inicio AT TIME ZONE CAST(:zona AS text) AS date) >= CAST(:desde AS date))
                  AND (CAST(:hasta AS date) IS NULL
                       OR CAST(c.cit_inicio AT TIME ZONE CAST(:zona AS text) AS date) <= CAST(:hasta AS date))
                  AND (CAST(:gradoId AS bigint) IS NULL OR g.grp_gra_id = CAST(:gradoId AS bigint))
                  AND (CAST(:grupoId AS bigint) IS NULL OR g.grp_id = CAST(:grupoId AS bigint))
                  AND (CAST(:categoriaId AS bigint) IS NULL OR EXISTS (
                        SELECT 1 FROM citas_alertas ca JOIN alertas a ON a.ale_id = ca.cia_ale_id
                        WHERE ca.cia_cit_id = c.cit_id AND a.ale_cat_id = CAST(:categoriaId AS bigint)))
                """;

        return (Object[]) consulta(sql, filtro, zona).getSingleResult();
    }

    // [valoraciones, estudiantes valorados]. no tienen categoria: ese filtro no aplica
    public Object[] valoraciones(FiltroEstadistica filtro, String zona) {

        String sql = """
                SELECT count(*), count(DISTINCT v.val_est_id)
                FROM valoraciones v
                JOIN matriculas m ON m.mat_id = v.val_mat_id
                JOIN grupos g ON g.grp_id = m.mat_grp_id
                WHERE (CAST(:anioId AS bigint) IS NULL OR m.mat_anl_id = CAST(:anioId AS bigint))
                  AND (CAST(:desde AS date) IS NULL
                       OR CAST(v.val_creado_en AT TIME ZONE CAST(:zona AS text) AS date) >= CAST(:desde AS date))
                  AND (CAST(:hasta AS date) IS NULL
                       OR CAST(v.val_creado_en AT TIME ZONE CAST(:zona AS text) AS date) <= CAST(:hasta AS date))
                  AND (CAST(:gradoId AS bigint) IS NULL OR g.grp_gra_id = CAST(:gradoId AS bigint))
                  AND (CAST(:grupoId AS bigint) IS NULL OR g.grp_id = CAST(:grupoId AS bigint))
                """;

        return (Object[]) consulta(sql, filtro, zona).getSingleResult();
    }

    // [yyyy-mm, total]
    public List<Object[]> porMes(FiltroEstadistica filtro, String zona) {

        String sql = "SELECT to_char(a.ale_creado_en AT TIME ZONE CAST(:zona AS text), 'YYYY-MM') AS mes, count(*) "
                + ALERTAS + FILTRO_ALERTAS + " GROUP BY 1 ORDER BY 1";

        return filas(sql, filtro, zona);
    }

    // [nombre de la categoria, total], de la que mas tiene a la que menos
    public List<Object[]> porCategoria(FiltroEstadistica filtro, String zona) {

        String sql = "SELECT c.cat_nombre, count(*) " + ALERTAS
                + " JOIN categorias_alerta c ON c.cat_id = a.ale_cat_id " + FILTRO_ALERTAS
                + " GROUP BY c.cat_nombre ORDER BY count(*) DESC, c.cat_nombre";

        return filas(sql, filtro, zona);
    }

    // [nivel, total]
    public List<Object[]> porNivel(FiltroEstadistica filtro, String zona) {

        String sql = "SELECT a.ale_nivel, count(*) " + ALERTAS + FILTRO_ALERTAS + " GROUP BY a.ale_nivel";

        return filas(sql, filtro, zona);
    }

    // [grado, grupo, anio, total] en el orden de los grados
    public List<Object[]> porGrupo(FiltroEstadistica filtro, String zona) {

        String sql = "SELECT gr.gra_nombre, g.grp_nombre, an.anl_anio, count(*) " + ALERTAS
                + " JOIN grados gr ON gr.gra_id = g.grp_gra_id JOIN anios_lectivos an ON an.anl_id = g.grp_anl_id "
                + FILTRO_ALERTAS
                + " GROUP BY an.anl_anio, gr.gra_orden, gr.gra_nombre, g.grp_nombre"
                + " ORDER BY an.anl_anio, gr.gra_orden, g.grp_nombre";

        return filas(sql, filtro, zona);
    }

    // [F, M, O o N (sin dato), total]
    public List<Object[]> porGenero(FiltroEstadistica filtro, String zona) {

        String sql = "SELECT coalesce(CAST(e.est_genero AS text), 'N'), count(*) " + ALERTAS
                + " JOIN estudiantes e ON e.est_id = a.ale_est_id " + FILTRO_ALERTAS
                + " GROUP BY 1";

        return filas(sql, filtro, zona);
    }

    // [rango de edad que tenia el estudiante cuando se creo la alerta, total]. N: sin fecha de nacimiento
    public List<Object[]> porEdad(FiltroEstadistica filtro, String zona) {

        String sql = """
                SELECT CASE
                           WHEN e.est_fecha_nacimiento IS NULL THEN 'N'
                           WHEN ed.edad <= 8 THEN 'HASTA_8'
                           WHEN ed.edad <= 11 THEN 'DE_9_A_11'
                           WHEN ed.edad <= 14 THEN 'DE_12_A_14'
                           WHEN ed.edad <= 17 THEN 'DE_15_A_17'
                           ELSE 'DESDE_18'
                       END,
                       count(*)
                """ + ALERTAS + """
                JOIN estudiantes e ON e.est_id = a.ale_est_id
                CROSS JOIN LATERAL (
                    SELECT extract(year FROM age(CAST(a.ale_creado_en AT TIME ZONE CAST(:zona AS text) AS date),
                                                 e.est_fecha_nacimiento)) AS edad
                ) ed
                """ + FILTRO_ALERTAS + " GROUP BY 1";

        return filas(sql, filtro, zona);
    }

    // [quien la creo: ESTUDIANTE (pidio ayuda) o el rol del que la reporto, total]
    public List<Object[]> porOrigen(FiltroEstadistica filtro, String zona) {

        String sql = "SELECT CASE WHEN a.ale_origen = 'ESTUDIANTE' THEN 'ESTUDIANTE' ELSE u.usu_rol END, count(*) "
                + ALERTAS + " JOIN usuarios u ON u.usu_id = a.ale_reportada_por " + FILTRO_ALERTAS
                + " GROUP BY 1";

        return filas(sql, filtro, zona);
    }

    // [nombre del psicorientador, alertas a su cargo, completadas]
    public List<Object[]> porPsicorientador(FiltroEstadistica filtro, String zona) {

        String sql = "SELECT p.per_nombres || ' ' || p.per_apellidos, count(*), "
                + "count(*) FILTER (WHERE a.ale_estado = 'COMPLETADA') "
                + ALERTAS + " JOIN personal p ON p.per_id = a.ale_psi_id " + FILTRO_ALERTAS
                + " GROUP BY p.per_id, p.per_nombres, p.per_apellidos ORDER BY count(*) DESC, 1";

        return filas(sql, filtro, zona);
    }

    // nombres para el encabezado del excel (null si no existe)
    public String nombreAnio(Long id) {
        return texto("SELECT CAST(anl_anio AS text) FROM anios_lectivos WHERE anl_id = :id", id);
    }

    public String nombreGrado(Long id) {
        return texto("SELECT gra_nombre FROM grados WHERE gra_id = :id", id);
    }

    public String nombreGrupo(Long id) {
        return texto("SELECT gr.gra_nombre || ' ' || g.grp_nombre FROM grupos g JOIN grados gr ON gr.gra_id = g.grp_gra_id "
                + "WHERE g.grp_id = :id", id);
    }

    public String nombreCategoria(Long id) {
        return texto("SELECT cat_nombre FROM categorias_alerta WHERE cat_id = :id", id);
    }

    // ---------------------------------------------------------------- ayudas

    @SuppressWarnings("unchecked")
    private List<Object[]> filas(String sql, FiltroEstadistica filtro, String zona) {
        return consulta(sql, filtro, zona).getResultList();
    }

    private Query consulta(String sql, FiltroEstadistica filtro, String zona) {

        Query query = em.createNativeQuery(sql);
        poner(query, sql, "anioId", filtro.anioId());
        poner(query, sql, "desde", fecha(filtro.desde()));
        poner(query, sql, "hasta", fecha(filtro.hasta()));
        poner(query, sql, "gradoId", filtro.gradoId());
        poner(query, sql, "grupoId", filtro.grupoId());
        poner(query, sql, "categoriaId", filtro.categoriaId());
        poner(query, sql, "zona", zona);
        return query;
    }

    // solo los que usa esa consulta (las valoraciones no tienen categoria)
    private void poner(Query query, String sql, String nombre, Object valor) {

        if (sql.contains(":" + nombre)) {
            query.setParameter(nombre, valor);
        }
    }

    // como texto: asi postgres recibe el null con tipo y el CAST lo vuelve fecha
    private String fecha(LocalDate fecha) {

        if (fecha == null) {
            return null;
        }
        return fecha.toString();
    }

    @SuppressWarnings("unchecked")
    private String texto(String sql, Long id) {

        if (id == null) {
            return null;
        }

        List<Object> resultado = em.createNativeQuery(sql).setParameter("id", id).getResultList();
        if (resultado.isEmpty()) {
            return null;
        }
        return (String) resultado.get(0);
    }
}
