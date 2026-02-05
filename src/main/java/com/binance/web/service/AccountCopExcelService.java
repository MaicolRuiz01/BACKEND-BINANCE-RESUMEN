package com.binance.web.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.Entity.AccountCop;
import com.binance.web.Repository.AccountCopRepository;
import com.binance.web.Repository.SaleP2pAccountCopRepository;
import com.binance.web.movimientos.MovimientoVistaService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountCopExcelService {

    private final AccountCopRepository accountCopRepository;
    private final MovimientoVistaService movimientoVistaService;
    private final SaleP2pAccountCopRepository saleP2pAccountCopRepository;

    public byte[] exportAccountCop(Integer cuentaId) throws Exception {

        AccountCop acc = accountCopRepository.findById(cuentaId)
                .orElseThrow(() -> new RuntimeException("Cuenta COP no encontrada"));

        var movimientos = movimientoVistaService.vistaPorCuentaCop(cuentaId);
        var detallesP2P = saleP2pAccountCopRepository.findByAccountCop_IdOrderByIdDesc(cuentaId);

        double balance = acc.getBalance() != null ? acc.getBalance() : 0.0;
        double cupoMax = acc.getCupoDiarioMax() != null ? acc.getCupoDiarioMax() : 0.0;
        double cupoHoy = acc.getCupoDisponibleHoy() != null ? acc.getCupoDisponibleHoy() : 0.0;

        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {

            // ================= RESUMEN =================
            var sh0 = wb.createSheet("Resumen");
            int r = 0;

            var a = sh0.createRow(r++);
            a.createCell(0).setCellValue("Cuenta");
            a.createCell(1).setCellValue(nvl(acc.getName()));

            var b = sh0.createRow(r++);
            b.createCell(0).setCellValue("Banco");
            b.createCell(1).setCellValue(acc.getBankType() != null ? acc.getBankType().name() : "");

            var c = sh0.createRow(r++);
            c.createCell(0).setCellValue("Balance (COP)");
            c.createCell(1).setCellValue(balance);

            var d = sh0.createRow(r++);
            d.createCell(0).setCellValue("Cupo Diario Max");
            d.createCell(1).setCellValue(cupoMax);

            var e = sh0.createRow(r++);
            e.createCell(0).setCellValue("Cupo Disponible Hoy");
            e.createCell(1).setCellValue(cupoHoy);

            var f = sh0.createRow(r++);
            f.createCell(0).setCellValue("Fecha Cupo");
            f.createCell(1).setCellValue(acc.getCupoFecha() != null ? acc.getCupoFecha().toString() : "");

            for (int col = 0; col <= 1; col++) sh0.autoSizeColumn(col);

            // ================= MOVIMIENTOS =================
            var sh1 = wb.createSheet("Movimientos");
            int i = 0;

            var h1 = sh1.createRow(i++);
            h1.createCell(0).setCellValue("Fecha");
            h1.createCell(1).setCellValue("Tipo");
            h1.createCell(2).setCellValue("Detalle");
            h1.createCell(3).setCellValue("Monto (signed)");
            h1.createCell(4).setCellValue("Entrada");
            h1.createCell(5).setCellValue("Salida");

            for (var m : movimientos) {
                var rr = sh1.createRow(i++);
                rr.createCell(0).setCellValue(m.getFecha() != null ? m.getFecha().toString() : "");
                rr.createCell(1).setCellValue(nvl(m.getTipo()));
                rr.createCell(2).setCellValue(nvl(m.getDetalle()));
                rr.createCell(3).setCellValue(m.getMontoSigned() != null ? m.getMontoSigned() : 0.0);
                rr.createCell(4).setCellValue(m.isEntrada() ? "SI" : "");
                rr.createCell(5).setCellValue(m.isSalida() ? "SI" : "");
            }
            for (int col = 0; col <= 5; col++) sh1.autoSizeColumn(col);

            // ================= VENTAS P2P =================
            var sh2 = wb.createSheet("Ventas P2P");
            int j = 0;

            var h2 = sh2.createRow(j++);
            h2.createCell(0).setCellValue("Fecha Venta");
            h2.createCell(1).setCellValue("OrderNumber");
            h2.createCell(2).setCellValue("Binance Account");
            h2.createCell(3).setCellValue("Pesos Venta (COP)");
            h2.createCell(4).setCellValue("USDT");
            h2.createCell(5).setCellValue("Comisión");
            h2.createCell(6).setCellValue("Tasa");
            h2.createCell(7).setCellValue("Asignado?");
            h2.createCell(8).setCellValue("Monto Asignado a ESTA cuenta");
            h2.createCell(9).setCellValue("Detalle ID");

            for (var dP2P : detallesP2P) {
                var sale = dP2P.getSaleP2p();

                var rr = sh2.createRow(j++);

                rr.createCell(0).setCellValue(sale != null && sale.getDate() != null ? sale.getDate().toString() : "");
                rr.createCell(1).setCellValue(sale != null ? nvl(sale.getNumberOrder()) : "");
                rr.createCell(2).setCellValue(
                        (sale != null && sale.getBinanceAccount() != null) ? nvl(sale.getBinanceAccount().getName()) : ""
                );

                rr.createCell(3).setCellValue(sale != null && sale.getPesosCop() != null ? sale.getPesosCop() : 0.0);
                rr.createCell(4).setCellValue(sale != null && sale.getDollarsUs() != null ? sale.getDollarsUs() : 0.0);
                rr.createCell(5).setCellValue(sale != null && sale.getCommission() != null ? sale.getCommission() : 0.0);
                rr.createCell(6).setCellValue(sale != null && sale.getTasa() != null ? sale.getTasa() : 0.0);
                rr.createCell(7).setCellValue(sale != null && Boolean.TRUE.equals(sale.getAsignado()) ? "SI" : "NO");

                rr.createCell(8).setCellValue(dP2P.getAmount() != null ? dP2P.getAmount() : 0.0);
                rr.createCell(9).setCellValue(dP2P.getId() != null ? dP2P.getId() : 0);
            }

            for (int col = 0; col <= 9; col++) sh2.autoSizeColumn(col);

            // salida
            try (var out = new java.io.ByteArrayOutputStream()) {
                wb.write(out);
                return out.toByteArray();
            }
        }
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}

