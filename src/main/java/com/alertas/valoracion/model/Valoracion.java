package com.alertas.valoracion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

// valoracion de rutina: el psicorientador vio al estudiante y escribio lo que encontro. no es una alerta
@Entity
@Table(name = "valoraciones")
@Getter
@Setter
public class Valoracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "val_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "val_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    @Column(name = "val_codigo", nullable = false, updatable = false, length = 16)
    private String codigo;

    @Column(name = "val_est_id", nullable = false, updatable = false)
    private Long estudianteId;

    @Column(name = "val_mat_id", nullable = false, updatable = false)
    private Long matriculaId;

    @Column(name = "val_psi_id", nullable = false, updatable = false)
    private Long psicorientadorId;

    @Column(name = "val_observacion", nullable = false, updatable = false)
    private String observacion;

    @Column(name = "val_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = OffsetDateTime.now();
    }
}
