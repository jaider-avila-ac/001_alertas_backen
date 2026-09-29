package com.alertas.cita.repository;

import com.alertas.cita.model.Cita;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// RLS ya filtra por institucion
public interface CitaRepository extends JpaRepository<Cita, Long> {

    Cita findByCodigo(String codigo);

    // usuarios de los estudiantes cuya cita se va a cancelar porque su psicorientador se inactivo
    @Query(value = """
            SELECT e.est_usu_id FROM citas c JOIN estudiantes e ON e.est_id = c.cit_est_id
            WHERE c.cit_estado = 'PROGRAMADA'
              AND c.cit_psi_id IN (SELECT p.per_id FROM personal p JOIN usuarios u ON u.usu_id = p.per_usu_id
                                   WHERE NOT u.usu_activo)
            """, nativeQuery = true)
    List<Long> estudiantesConCitaDeInactivos();

    // las citas programadas de psicorientadores inactivos se cancelan
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE citas SET cit_estado = 'CANCELADA', cit_motivo_cancelacion = :motivo, cit_cerrada_en = now(),
                             cit_version = cit_version + 1
            WHERE cit_estado = 'PROGRAMADA'
              AND cit_psi_id IN (SELECT p.per_id FROM personal p JOIN usuarios u ON u.usu_id = p.per_usu_id
                                 WHERE NOT u.usu_activo)
            """, nativeQuery = true)
    int cancelarDeInactivos(@Param("motivo") String motivo);

    // solo puede haber una programada por estudiante (indice unico)
    Cita findByEstudianteIdAndEstado(Long estudianteId, String estado);

    List<Cita> findByEstudianteIdOrderByInicioDesc(Long estudianteId);

    // agenda del psicorientador en un rango
    @Query("SELECT c FROM Cita c WHERE c.psicorientadorId = :psicorientadorId "
            + "AND c.inicio >= :desde AND c.inicio < :hasta ORDER BY c.inicio")
    List<Cita> agenda(
            @Param("psicorientadorId") Long psicorientadorId,
            @Param("desde") OffsetDateTime desde,
            @Param("hasta") OffsetDateTime hasta);

    // nombres para mostrar la cita: [codigo, nombres y apellidos del estudiante, grado, grupo,
    //  nombres y apellidos del psicorientador]
    @Query(value = """
            SELECT e.est_codigo, e.est_nombres, e.est_apellidos, gr.gra_nombre, g.grp_nombre,
                   p.per_nombres, p.per_apellidos
            FROM citas c
            JOIN estudiantes e ON e.est_id = c.cit_est_id
            JOIN grupos g ON g.grp_id = c.cit_grp_id
            JOIN grados gr ON gr.gra_id = g.grp_gra_id
            JOIN personal p ON p.per_id = c.cit_psi_id
            WHERE c.cit_id = :citaId
            """, nativeQuery = true)
    List<Object[]> nombres(@Param("citaId") Long citaId);

    // ---- alertas que trata la cita (tabla citas_alertas) ----

    @Modifying
    @Query(value = "INSERT INTO citas_alertas (cia_ins_id, cia_cit_id, cia_ale_id, cia_est_id) "
            + "VALUES (:institucionId, :citaId, :alertaId, :estudianteId)", nativeQuery = true)
    void agregarAlerta(
            @Param("institucionId") Long institucionId,
            @Param("citaId") Long citaId,
            @Param("alertaId") Long alertaId,
            @Param("estudianteId") Long estudianteId);

    @Modifying
    @Query(value = "UPDATE citas_alertas SET cia_resultado = :resultado, cia_observacion = :observacion "
            + "WHERE cia_cit_id = :citaId AND cia_ale_id = :alertaId", nativeQuery = true)
    void guardarResultado(
            @Param("citaId") Long citaId,
            @Param("alertaId") Long alertaId,
            @Param("resultado") String resultado,
            @Param("observacion") String observacion);

    // [id de la alerta, codigo, categoria, nivel, resultado en la cita, observacion, descripcion de la alerta]
    @Query(value = """
            SELECT a.ale_id, a.ale_codigo, c.cat_nombre, a.ale_nivel, ca.cia_resultado, ca.cia_observacion,
                   a.ale_descripcion
            FROM citas_alertas ca
            JOIN alertas a ON a.ale_id = ca.cia_ale_id
            JOIN categorias_alerta c ON c.cat_id = a.ale_cat_id
            WHERE ca.cia_cit_id = :citaId
            ORDER BY a.ale_creado_en
            """, nativeQuery = true)
    List<Object[]> alertasDeCita(@Param("citaId") Long citaId);
}
