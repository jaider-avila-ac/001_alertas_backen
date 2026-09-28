package com.alertas.categoria.model;

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

@Entity
@Table(name = "categorias_alerta")
@Getter
@Setter
public class CategoriaAlerta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cat_id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "cat_ins_id", nullable = false, updatable = false)
    private Long institucionId;

    @Column(name = "cat_nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "cat_activa", nullable = false)
    private boolean activa = true;

    @Column(name = "cat_creado_en", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime creadoEn;

    @PrePersist
    void alCrear() {
        creadoEn = OffsetDateTime.now();
    }
}
