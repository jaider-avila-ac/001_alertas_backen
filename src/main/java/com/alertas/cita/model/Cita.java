package com.alertas.cita.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

// una cita atiende las alertas activas del estudiante. las alertas que trata van en citas_alertas
@Entity
@Table(name = "citas")
@Getter
@Setter
public class Cita {

    public static final String PROGRAMADA = "PROGRAMADA";
    public static final String REALIZADA = "REALIZADA";
    public static final String NO_ASISTIO = "NO_ASISTIO";
    public static final String CANCELADA = "CANCELADA";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cit_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "cit_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    @Column(name = "cit_codigo", nullable = false, updatable = false, length = 16)
    private String codigo;

    @Column(name = "cit_est_id", nullable = false, updatable = false)
    private Long estudianteId;

    // psicorientador (personal). cambia si se reasigna al estudiante
    @Column(name = "cit_psi_id", nullable = false)
    private Long psicorientadorId;

    // matricula y grupo del momento, igual que en la alerta
    @Column(name = "cit_mat_id", nullable = false, updatable = false)
    private Long matriculaId;

    @Column(name = "cit_grp_id", nullable = false, updatable = false)
    private Long grupoId;

    @Column(name = "cit_inicio", nullable = false)
    private OffsetDateTime inicio;

    @Column(name = "cit_fin", nullable = false)
    private OffsetDateTime fin;

    @Column(name = "cit_modalidad", nullable = false, length = 10)
    private String modalidad;

    // salon o enlace de la reunion
    @Column(name = "cit_lugar", length = 200)
    private String lugar;

    // la ve el estudiante
    @Column(name = "cit_indicacion", length = 500)
    private String indicacion;

    @Column(name = "cit_estado", nullable = false, length = 12)
    private String estado = PROGRAMADA;

    // cuando el psicorientador presiono "Iniciar". la hora programada es solo una referencia
    @Column(name = "cit_iniciada_en")
    private OffsetDateTime iniciadaEn;

    @Column(name = "cit_motivo_cancelacion", length = 300)
    private String motivoCancelacion;

    @Column(name = "cit_cerrada_en")
    private OffsetDateTime cerradaEn;

    // usuario que la agendo
    @Column(name = "cit_creada_por", nullable = false, updatable = false)
    private Long creadaPor;

    @Version
    @Column(name = "cit_version", nullable = false)
    @Setter(AccessLevel.NONE)
    private int version;

    @Column(name = "cit_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @Column(name = "cit_actualizado_en", nullable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime actualizadoEn;

    public boolean isProgramada() {
        return PROGRAMADA.equals(estado);
    }

    public boolean isEnCurso() {
        return isProgramada() && iniciadaEn != null;
    }

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
