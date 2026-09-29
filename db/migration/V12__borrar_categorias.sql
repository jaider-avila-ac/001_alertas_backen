-- una categoria sin alertas se puede borrar (con alertas solo se desactiva, la fk lo impide)
GRANT DELETE ON categorias_alerta TO alertas_app;
