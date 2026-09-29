package com.alertas.sms.repository;

import com.alertas.sms.model.SmsEnvio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// RLS ya filtra por institucion. los datos de estudiante y familiares se leen con sql
// para que este modulo no dependa de los demas
public interface SmsEnvioRepository extends JpaRepository<SmsEnvio, Long> {

    // [nombre de la institucion, sms activo]
    @Query(value = "SELECT ins_nombre, ins_sms_activo FROM instituciones WHERE ins_id = :institucionId", nativeQuery = true)
    List<Object[]> institucion(@Param("institucionId") Long institucionId);

    // [nombres, apellidos, celular, sms a familiares activo]
    @Query(value = "SELECT est_nombres, est_apellidos, est_celular, est_sms_familiares FROM estudiantes WHERE est_id = :estudianteId",
            nativeQuery = true)
    List<Object[]> estudiante(@Param("estudianteId") Long estudianteId);

    // celulares de los familiares marcados "recibe sms" (maximo 3)
    @Query(value = "SELECT fam_celular FROM familiares WHERE fam_est_id = :estudianteId AND fam_recibe_sms "
            + "AND fam_celular IS NOT NULL ORDER BY fam_posicion", nativeQuery = true)
    List<String> celularesFamiliares(@Param("estudianteId") Long estudianteId);

    // [id del estudiante, inicio de la cita]
    @Query(value = "SELECT cit_est_id, cit_inicio FROM citas WHERE cit_id = :citaId", nativeQuery = true)
    List<Object[]> cita(@Param("citaId") Long citaId);

    // en una cita solo por solicitudes del estudiante, la familia se entera solo si el lo autorizo.
    // cuenta las alertas de la cita que permiten avisar a la familia
    @Query(value = """
            SELECT count(*) FROM citas_alertas ca JOIN alertas a ON a.ale_id = ca.cia_ale_id
            WHERE ca.cia_cit_id = :citaId
              AND (a.ale_origen = 'DOCENTE' OR a.ale_autoriza_sms_familiares)
            """, nativeQuery = true)
    long alertasQuePermitenFamilia(@Param("citaId") Long citaId);
}
