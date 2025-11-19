package com.binance.web.Ajustes;

import com.binance.web.Entity.Ajustes;
import java.util.List;

public interface AjustesService {

    List<Ajustes> allAjustes();
    Ajustes crearAjuste(Ajustes ajuste);
    List<Ajustes> obtenerajustesporUsuarioCL(int id);
    List<Ajustes> obtenerajustesporUsuarioPR(int id);

}
