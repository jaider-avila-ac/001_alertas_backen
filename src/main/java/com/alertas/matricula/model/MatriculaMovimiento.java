package com.alertas.matricula.model;

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

// cambio de grupo (o de grado) dentro del mismo anio. solo se agregan, nunca se editan
@Entity
@Table(name = "matricula_movimientos")
@Getter
@Setter
public class MatriculaMovimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mov_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "mov_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    @Column(name = "mov_mat_id", nullable = false, updatable = false)
    private Long matriculaId;

    @Column(name = "mov_anl_id", nullable = false, updatable = false)
    private Long anioId;

    @Column(name = "mov_grp_anterior_id", nullable = false, updatable = false)
    private Long grupoAnteriorId;

    @Column(name = "mov_grp_nuevo_id", nullable = false, updatable = false)
    private Long grupoNuevoId;

    @Column(name = "mov_motivo", length = 300, updatable = false)
    private String motivo;

    @Column(name = "mov_usu_id", nullable = false, updatable = false)
    private Long usuarioId;

    @Column(name = "mov_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = OffsetDateTime.now();
    }
}
