package com.binance.web.conciliacion;

import java.util.ArrayList;
import java.util.List;

/** Resumen de qué se actualizó al procesar un ConciliacionResultadoDto — para que
 *  el bot vea de una vez en su propio log si algún nombre no encontró cuenta. */
public class ConciliacionResponseDto {

    private List<String> actualizados = new ArrayList<>();
    private List<String> noEncontrados = new ArrayList<>();

    public List<String> getActualizados() { return actualizados; }
    public void setActualizados(List<String> actualizados) { this.actualizados = actualizados; }
    public List<String> getNoEncontrados() { return noEncontrados; }
    public void setNoEncontrados(List<String> noEncontrados) { this.noEncontrados = noEncontrados; }
}
