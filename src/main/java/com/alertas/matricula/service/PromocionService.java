package com.alertas.matricula.service;

import com.alertas.matricula.dto.ConfirmarPromocionRequest;
import com.alertas.matricula.dto.PromocionResponse;
import com.alertas.matricula.dto.ResultadoPromocionResponse;

// paso de los estudiantes activos del anio activo al anio siguiente
public interface PromocionService {

    // que grupos faltan en el anio siguiente y cuantos estudiantes pasarian de cada grupo
    PromocionResponse vistaPrevia();

    // crea en el anio siguiente los grupos que faltan (la misma cantidad de grupos, en el grado siguiente)
    PromocionResponse prepararGrupos();

    // pasa a los activos al grupo destino y gradua a los del ultimo grado. se puede repetir sin duplicar
    ResultadoPromocionResponse confirmar(ConfirmarPromocionRequest request);
}
