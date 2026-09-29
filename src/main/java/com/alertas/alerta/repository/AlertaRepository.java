package com.alertas.alerta.repository;

import com.alertas.alerta.model.Alerta;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// RLS ya filtra por institucion
public interface AlertaRepository extends JpaRepository<Alerta, Long> {

    Alerta findByCodigo(String codigo);

    // pendientes y en proceso, de la mas vieja a la mas nueva
    @Query("SELECT a FROM Alerta a WHERE a.estudianteId = :estudianteId "
            + "AND a.estado IN ('PENDIENTE', 'EN_PROCESO') ORDER BY a.creadoEn")
    List<Alerta> activasDelEstudiante(@Param("estudianteId") Long estudianteId);

    // tomar al estudiante: solo las que nadie tiene. si otro psicorientador llego primero, no cambia ninguna
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE alertas SET ale_psi_id = :psicorientadorId, ale_asignada_en = now(), ale_version = ale_version + 1
            WHERE ale_est_id = :estudianteId AND ale_estado IN ('PENDIENTE', 'EN_PROCESO') AND ale_psi_id IS NULL
            """, nativeQuery = true)
    int tomar(@Param("estudianteId") Long estudianteId, @Param("psicorientadorId") Long psicorientadorId);

    // casos de psicorientadores inactivos: sus alertas activas quedan sin asignar (vuelven a la bandeja)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE alertas SET ale_psi_id = NULL, ale_asignada_en = NULL, ale_version = ale_version + 1
            WHERE ale_estado IN ('PENDIENTE', 'EN_PROCESO')
              AND ale_psi_id IN (SELECT p.per_id FROM personal p JOIN usuarios u ON u.usu_id = p.per_usu_id
                                 WHERE NOT u.usu_activo)
            """, nativeQuery = true)
    int liberarDeInactivos();

    // reasignar: todas las activas pasan al otro psicorientador
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE alertas SET ale_psi_id = :psicorientadorId, ale_asignada_en = now(), ale_version = ale_version + 1
            WHERE ale_est_id = :estudianteId AND ale_estado IN ('PENDIENTE', 'EN_PROCESO')
            """, nativeQuery = true)
    int asignar(@Param("estudianteId") Long estudianteId, @Param("psicorientadorId") Long psicorientadorId);

    // la cita programada del estudiante: [id de la cita, id del psicorientador]
    @Query(value = "SELECT c.cit_id, c.cit_psi_id FROM citas c WHERE c.cit_est_id = :estudianteId AND c.cit_estado = 'PROGRAMADA'",
            nativeQuery = true)
    List<Object[]> citaProgramada(@Param("estudianteId") Long estudianteId);

    @Modifying
    @Query(value = "INSERT INTO citas_alertas (cia_ins_id, cia_cit_id, cia_ale_id, cia_est_id) "
            + "VALUES (:institucionId, :citaId, :alertaId, :estudianteId)", nativeQuery = true)
    void pegarACita(
            @Param("institucionId") Long institucionId,
            @Param("citaId") Long citaId,
            @Param("alertaId") Long alertaId,
            @Param("estudianteId") Long estudianteId);

    // el psicorientador que ya atiende al estudiante (tiene alertas activas suyas y sigue activo)
    @Query(value = """
            SELECT a.ale_psi_id FROM alertas a
            JOIN personal p ON p.per_id = a.ale_psi_id
            JOIN usuarios u ON u.usu_id = p.per_usu_id
            WHERE a.ale_est_id = :estudianteId AND a.ale_estado IN ('PENDIENTE', 'EN_PROCESO') AND u.usu_activo
            ORDER BY a.ale_asignada_en DESC
            LIMIT 1
            """, nativeQuery = true)
    List<Long> psicorientadorActual(@Param("estudianteId") Long estudianteId);

    // alertas que creo un usuario, de la mas nueva a la mas vieja. estado null = todas
    @Query(value = """
            SELECT a.ale_codigo AS codigo, a.ale_origen AS origen,
                   e.est_codigo AS estudianteCodigo, e.est_nombres AS estudianteNombres, e.est_apellidos AS estudianteApellidos,
                   gr.gra_nombre AS gradoNombre, g.grp_nombre AS grupoNombre, an.anl_anio AS anio,
                   c.cat_nombre AS categoria, a.ale_nivel AS nivel, a.ale_estado AS estado,
                   a.ale_peligro_inmediato AS peligroInmediato, a.ale_prioritaria AS prioritaria,
                   a.ale_fecha_hecho AS fechaHecho, a.ale_creado_en AS creadoEn
            FROM alertas a
            JOIN estudiantes e ON e.est_id = a.ale_est_id
            JOIN grupos g ON g.grp_id = a.ale_grp_id
            JOIN grados gr ON gr.gra_id = g.grp_gra_id
            JOIN anios_lectivos an ON an.anl_id = a.ale_anl_id
            JOIN categorias_alerta c ON c.cat_id = a.ale_cat_id
            WHERE a.ale_reportada_por = :usuarioId
              AND (CAST(:estado AS text) IS NULL OR a.ale_estado = CAST(:estado AS text))
            ORDER BY a.ale_creado_en DESC
            """,
            countQuery = """
            SELECT count(*) FROM alertas a
            WHERE a.ale_reportada_por = :usuarioId
              AND (CAST(:estado AS text) IS NULL OR a.ale_estado = CAST(:estado AS text))
            """,
            nativeQuery = true)
    Page<AlertaFila> reportadasPor(@Param("usuarioId") Long usuarioId, @Param("estado") String estado, Pageable pageable);

    // detalle con los nombres ya resueltos. devuelve una fila con
    // [nombre de categoria, grado, grupo, anio, nombres de quien reporta, apellidos de quien reporta,
    //  nombres del psicorientador, apellidos del psicorientador, codigo, nombres y apellidos del estudiante]
    @Query(value = """
            SELECT c.cat_nombre, gr.gra_nombre, g.grp_nombre, an.anl_anio,
                   coalesce(rp.per_nombres, re.est_nombres), coalesce(rp.per_apellidos, re.est_apellidos),
                   ps.per_nombres, ps.per_apellidos,
                   e.est_codigo, e.est_nombres, e.est_apellidos
            FROM alertas a
            JOIN categorias_alerta c ON c.cat_id = a.ale_cat_id
            JOIN grupos g ON g.grp_id = a.ale_grp_id
            JOIN grados gr ON gr.gra_id = g.grp_gra_id
            JOIN anios_lectivos an ON an.anl_id = a.ale_anl_id
            JOIN estudiantes e ON e.est_id = a.ale_est_id
            LEFT JOIN personal rp ON rp.per_usu_id = a.ale_reportada_por
            LEFT JOIN estudiantes re ON re.est_usu_id = a.ale_reportada_por
            LEFT JOIN personal ps ON ps.per_id = a.ale_psi_id
            WHERE a.ale_id = :alertaId
            """, nativeQuery = true)
    List<Object[]> nombresDelDetalle(@Param("alertaId") Long alertaId);

    // ---- atencion (psicorientador) ----

    // estudiantes con alertas activas que nadie tiene (nuevas, o de un psicorientador que se inactivo).
    // primero las prioritarias, luego el nivel y la mas vieja
    @Query(value = """
            SELECT e.est_codigo AS codigo, e.est_nombres AS nombres, e.est_apellidos AS apellidos,
                   gr.gra_nombre AS gradoNombre, g.grp_nombre AS grupoNombre,
                   count(*) AS alertas,
                   max(CASE a.ale_nivel WHEN 'CRITICO' THEN 4 WHEN 'ALTO' THEN 3 WHEN 'MODERADO' THEN 2 ELSE 1 END) AS nivelMaximo,
                   bool_or(a.ale_prioritaria) AS prioritaria,
                   bool_or(a.ale_origen = 'ESTUDIANTE') AS pidioAyuda,
                   bool_or(a.ale_estado = 'EN_PROCESO') AS veniaEnAtencion,
                   min(a.ale_creado_en) AS desde
            FROM alertas a
            JOIN estudiantes e ON e.est_id = a.ale_est_id
            LEFT JOIN anios_lectivos an ON an.anl_activo
            LEFT JOIN matriculas m ON m.mat_est_id = e.est_id AND m.mat_anl_id = an.anl_id
            LEFT JOIN grupos g ON g.grp_id = m.mat_grp_id
            LEFT JOIN grados gr ON gr.gra_id = g.grp_gra_id
            WHERE a.ale_estado IN ('PENDIENTE', 'EN_PROCESO') AND a.ale_psi_id IS NULL
            GROUP BY e.est_id, e.est_codigo, e.est_nombres, e.est_apellidos, gr.gra_nombre, g.grp_nombre
            ORDER BY bool_or(a.ale_prioritaria) DESC,
                     max(CASE a.ale_nivel WHEN 'CRITICO' THEN 4 WHEN 'ALTO' THEN 3 WHEN 'MODERADO' THEN 2 ELSE 1 END) DESC,
                     min(a.ale_creado_en)
            """,
            countQuery = """
            SELECT count(DISTINCT a.ale_est_id) FROM alertas a
            WHERE a.ale_estado IN ('PENDIENTE', 'EN_PROCESO') AND a.ale_psi_id IS NULL
            """,
            nativeQuery = true)
    Page<BandejaFila> bandeja(Pageable pageable);

    // "mis estudiantes" del psicorientador por pestana:
    // POR_AGENDAR: con alertas activas y sin cita programada. CON_CITA: cita programada que no se ha iniciado.
    // EN_CURSO: cita iniciada que no se ha finalizado. HISTORIAL: sin alertas activas. la hora no cuenta
    // filtros opcionales: nivel y categoria (de sus alertas), grado y grupo (del anio activo)
    @Query(value = """
            WITH mios AS (
                SELECT a.ale_est_id AS est_id,
                       count(*) FILTER (WHERE a.ale_estado IN ('PENDIENTE', 'EN_PROCESO')) AS activas,
                       max(CASE WHEN a.ale_estado IN ('PENDIENTE', 'EN_PROCESO') THEN
                               CASE a.ale_nivel WHEN 'CRITICO' THEN 4 WHEN 'ALTO' THEN 3 WHEN 'MODERADO' THEN 2 ELSE 1 END
                           END) AS nivel_maximo,
                       coalesce(bool_or(a.ale_prioritaria AND a.ale_estado <> 'COMPLETADA'), false) AS prioritaria
                FROM alertas a
                WHERE a.ale_psi_id = :psicorientadorId
                GROUP BY a.ale_est_id
                HAVING (CAST(:nivel AS text) IS NULL OR bool_or(a.ale_nivel = CAST(:nivel AS text)))
                   AND (CAST(:categoriaId AS bigint) IS NULL OR bool_or(a.ale_cat_id = CAST(:categoriaId AS bigint)))
            )
            SELECT e.est_codigo AS codigo, e.est_nombres AS nombres, e.est_apellidos AS apellidos,
                   gr.gra_nombre AS gradoNombre, g.grp_nombre AS grupoNombre,
                   mios.activas AS activas, mios.nivel_maximo AS nivelMaximo, mios.prioritaria AS prioritaria,
                   c.cit_codigo AS citaCodigo, c.cit_inicio AS citaInicio, c.cit_fin AS citaFin
            FROM mios
            JOIN estudiantes e ON e.est_id = mios.est_id
            LEFT JOIN citas c ON c.cit_est_id = e.est_id AND c.cit_estado = 'PROGRAMADA'
            LEFT JOIN anios_lectivos an ON an.anl_activo
            LEFT JOIN matriculas m ON m.mat_est_id = e.est_id AND m.mat_anl_id = an.anl_id
            LEFT JOIN grupos g ON g.grp_id = m.mat_grp_id
            LEFT JOIN grados gr ON gr.gra_id = g.grp_gra_id
            WHERE ((:pestana = 'POR_AGENDAR' AND mios.activas > 0 AND c.cit_id IS NULL)
                OR (:pestana = 'CON_CITA' AND c.cit_psi_id = :psicorientadorId AND c.cit_iniciada_en IS NULL)
                OR (:pestana = 'EN_CURSO' AND c.cit_psi_id = :psicorientadorId AND c.cit_iniciada_en IS NOT NULL)
                OR (:pestana = 'HISTORIAL' AND mios.activas = 0))
              AND (CAST(:gradoId AS bigint) IS NULL OR g.grp_gra_id = CAST(:gradoId AS bigint))
              AND (CAST(:grupoId AS bigint) IS NULL OR g.grp_id = CAST(:grupoId AS bigint))
            ORDER BY c.cit_inicio NULLS LAST, mios.prioritaria DESC, mios.nivel_maximo DESC NULLS LAST,
                     e.est_apellidos, e.est_nombres
            """,
            countQuery = """
            WITH mios AS (
                SELECT a.ale_est_id AS est_id,
                       count(*) FILTER (WHERE a.ale_estado IN ('PENDIENTE', 'EN_PROCESO')) AS activas
                FROM alertas a
                WHERE a.ale_psi_id = :psicorientadorId
                GROUP BY a.ale_est_id
                HAVING (CAST(:nivel AS text) IS NULL OR bool_or(a.ale_nivel = CAST(:nivel AS text)))
                   AND (CAST(:categoriaId AS bigint) IS NULL OR bool_or(a.ale_cat_id = CAST(:categoriaId AS bigint)))
            )
            SELECT count(*)
            FROM mios
            JOIN estudiantes e ON e.est_id = mios.est_id
            LEFT JOIN citas c ON c.cit_est_id = e.est_id AND c.cit_estado = 'PROGRAMADA'
            LEFT JOIN anios_lectivos an ON an.anl_activo
            LEFT JOIN matriculas m ON m.mat_est_id = e.est_id AND m.mat_anl_id = an.anl_id
            LEFT JOIN grupos g ON g.grp_id = m.mat_grp_id
            WHERE ((:pestana = 'POR_AGENDAR' AND mios.activas > 0 AND c.cit_id IS NULL)
                OR (:pestana = 'CON_CITA' AND c.cit_psi_id = :psicorientadorId AND c.cit_iniciada_en IS NULL)
                OR (:pestana = 'EN_CURSO' AND c.cit_psi_id = :psicorientadorId AND c.cit_iniciada_en IS NOT NULL)
                OR (:pestana = 'HISTORIAL' AND mios.activas = 0))
              AND (CAST(:gradoId AS bigint) IS NULL OR g.grp_gra_id = CAST(:gradoId AS bigint))
              AND (CAST(:grupoId AS bigint) IS NULL OR g.grp_id = CAST(:grupoId AS bigint))
            """,
            nativeQuery = true)
    Page<EstudianteAtencionFila> misEstudiantes(
            @Param("psicorientadorId") Long psicorientadorId,
            @Param("pestana") String pestana,
            @Param("nivel") String nivel,
            @Param("categoriaId") Long categoriaId,
            @Param("gradoId") Long gradoId,
            @Param("grupoId") Long grupoId,
            Pageable pageable);

    // todas las alertas del estudiante con los nombres resueltos, de la mas nueva a la mas vieja
    @Query(value = """
            SELECT a.ale_codigo AS codigo, a.ale_origen AS origen, a.ale_estado AS estado, a.ale_nivel AS nivel,
                   a.ale_prioritaria AS prioritaria, a.ale_peligro_inmediato AS peligroInmediato,
                   c.cat_nombre AS categoria, a.ale_descripcion AS descripcion, a.ale_fecha_hecho AS fechaHecho,
                   a.ale_lugar AS lugar, a.ale_horario_seguro AS horarioSeguro,
                   a.ale_modalidad_preferida AS modalidadPreferida, a.ale_autoriza_sms_familiares AS autorizaSmsFamiliares,
                   gr.gra_nombre AS gradoNombre, g.grp_nombre AS grupoNombre, an.anl_anio AS anio,
                   coalesce(rp.per_nombres || ' ' || rp.per_apellidos, re.est_nombres || ' ' || re.est_apellidos) AS reportadaPor,
                   ps.per_nombres || ' ' || ps.per_apellidos AS psicorientador,
                   a.ale_conclusion AS conclusion, a.ale_creado_en AS creadoEn, a.ale_completada_en AS completadaEn
            FROM alertas a
            JOIN categorias_alerta c ON c.cat_id = a.ale_cat_id
            JOIN grupos g ON g.grp_id = a.ale_grp_id
            JOIN grados gr ON gr.gra_id = g.grp_gra_id
            JOIN anios_lectivos an ON an.anl_id = a.ale_anl_id
            LEFT JOIN personal rp ON rp.per_usu_id = a.ale_reportada_por
            LEFT JOIN estudiantes re ON re.est_usu_id = a.ale_reportada_por
            LEFT JOIN personal ps ON ps.per_id = a.ale_psi_id
            WHERE a.ale_est_id = :estudianteId
            ORDER BY a.ale_creado_en DESC
            """, nativeQuery = true)
    List<AlertaExpedienteFila> delEstudiante(@Param("estudianteId") Long estudianteId);

    // lo que se ha escrito de cada alerta en las citas: [codigo de la alerta, fecha de la cita, observacion, resultado]
    @Query(value = """
            SELECT a.ale_codigo, coalesce(c.cit_iniciada_en, c.cit_inicio), ca.cia_observacion, ca.cia_resultado
            FROM citas_alertas ca
            JOIN alertas a ON a.ale_id = ca.cia_ale_id
            JOIN citas c ON c.cit_id = ca.cia_cit_id
            WHERE a.ale_est_id = :estudianteId AND ca.cia_observacion IS NOT NULL
            ORDER BY c.cit_inicio
            """, nativeQuery = true)
    List<Object[]> seguimientosDelEstudiante(@Param("estudianteId") Long estudianteId);
}
