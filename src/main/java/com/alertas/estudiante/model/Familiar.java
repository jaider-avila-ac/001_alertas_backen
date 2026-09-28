package com.alertas.estudiante.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

// maximo 3 por estudiante: la posicion va de 1 a 3 y la bd no deja repetirla
@Entity
@Table(name = "familiares")
@Getter
@Setter
public class Familiar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fam_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "fam_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fam_est_id", nullable = false, updatable = false)
    private Estudiante estudiante;

    @Column(name = "fam_posicion", nullable = false)
    private int posicion;

    @Column(name = "fam_nombres", nullable = false, length = 80)
    private String nombres;

    @Column(name = "fam_apellidos", length = 80)
    private String apellidos;

    @Column(name = "fam_parentesco", nullable = false, length = 20)
    private String parentesco;

    @Column(name = "fam_celular", length = 10)
    private String celular;

    @Column(name = "fam_recibe_sms", nullable = false)
    private boolean recibeSms;

    @Column(name = "fam_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @Column(name = "fam_actualizado_en", nullable = false)
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
