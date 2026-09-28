package com.alertas.estudiante.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

// en que grupo esta el estudiante en cada anio. una por anio.
// el anio y el grupo se guardan como id para no amarrar este modulo a las entidades de estructura
@Entity
@Table(name = "ubicaciones")
@Getter
@Setter
public class Ubicacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ubi_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "ubi_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    @Column(name = "ubi_est_id", nullable = false, updatable = false)
    private Long estudianteId;

    @Column(name = "ubi_anl_id", nullable = false, updatable = false)
    private Long anioId;

    @Column(name = "ubi_grp_id", nullable = false)
    private Long grupoId;

    @Column(name = "ubi_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @Column(name = "ubi_actualizado_en", nullable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime actualizadoEn;

    @PrePersist
    void alCrear() {

        creadoEn = OffsetDateTime.now();
        actualizadoEn = creadoEn;
    }

    @PreUpdate
    void alActualizar() {
        actualizadoEn = OffsetDateTime.now();
    }
}
