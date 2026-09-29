package com.alertas.notificacion.repository;

import com.alertas.notificacion.model.Notificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// RLS ya filtra por institucion; aqui ademas siempre por usuario
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    Page<Notificacion> findByUsuarioIdOrderByCreadoEnDesc(Long usuarioId, Pageable pageable);

    long countByUsuarioIdAndLeidaFalse(Long usuarioId);

    Notificacion findByIdAndUsuarioId(Long id, Long usuarioId);

    @Modifying
    @Query("UPDATE Notificacion n SET n.leida = true WHERE n.usuarioId = :usuarioId AND n.leida = false")
    int marcarTodasLeidas(@Param("usuarioId") Long usuarioId);
}
