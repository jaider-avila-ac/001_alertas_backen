package com.alertas.institucion.dto;

import com.alertas.personal.dto.AdministradorResponse;

public record CrearInstitucionResponse(InstitucionResponse institucion, AdministradorResponse administrador) {
}
