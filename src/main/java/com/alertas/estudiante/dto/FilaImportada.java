package com.alertas.estudiante.dto;

// una fila del excel ya validada. se guarda en redis mientras el admin confirma
public record FilaImportada(
        int fila,
        String tipoDoc,
        String nroDoc,
        String nombres,
        String apellidos,
        String genero,
        String fechaNacimiento,
        String celular,
        Long gradoId,
        String gradoNombre,
        String grupoNombre,
        String familiarNombres,
        String familiarParentesco,
        String familiarCelular,
        String correo,
        String direccion,
        String barrio,
        String eps,
        String rh,
        String condicionesSalud) {
}
