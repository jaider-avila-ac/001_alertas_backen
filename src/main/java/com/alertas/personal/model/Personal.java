package com.alertas.personal.model;

import com.alertas.usuario.model.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

// admin, docente o psicorientador. el rol esta en el usuario
@Entity
@Table(name = "personal")
@Getter
@Setter
public class Personal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "per_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "per_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    // lo que ve el front en vez del id
    @Column(name = "per_codigo", nullable = false, updatable = false, length = 16)
    private String codigo;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "per_usu_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "per_tipo_doc", nullable = false, length = 3)
    private String tipoDoc;

    @Column(name = "per_nro_doc", nullable = false, length = 20)
    private String nroDoc;

    @Column(name = "per_nombres", nullable = false, length = 80)
    private String nombres;

    @Column(name = "per_apellidos", nullable = false, length = 80)
    private String apellidos;

    @Column(name = "per_correo", length = 120)
    private String correo;

    @Column(name = "per_celular", length = 10)
    private String celular;

    @Column(name = "per_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @Column(name = "per_actualizado_en", nullable = false)
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
