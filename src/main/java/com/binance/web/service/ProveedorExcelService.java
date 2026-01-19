package com.binance.web.service;

import org.springframework.stereotype.Service;

import com.binance.web.Entity.Supplier;
import com.binance.web.Repository.BuyDollarsRepository;
import com.binance.web.Repository.SellDollarsRepository;
import com.binance.web.Repository.SupplierRepository;
import com.binance.web.movimientos.MovimientoVistaService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProveedorExcelService {

    private final MovimientoVistaService movimientoVistaService;
    private final SupplierRepository supplierRepository;
    private final BuyDollarsRepository buyDollarsRepository;
    private final SellDollarsRepository sellDollarsRepository;

    public byte[] exportProveedor(Integer proveedorId) throws Exception {

        Supplier prov = supplierRepository.findById(proveedorId)
                .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));

        var movimientos = movimientoVistaService.vistaPorProveedor(proveedorId);
        var compras = buyDollarsRepository.findBySupplier_IdOrderByDateDesc(proveedorId);
        var ventas  = sellDollarsRepository.findBySupplier_IdOrderByDateDesc(proveedorId);

        double saldo = prov.getBalance() != null ? prov.getBalance() : 0.0;
        String estado = saldo > 0 ? "LE DEBEMOS" : (saldo < 0 ? "NOS DEBE" : "EN PAZ");

        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {

            // ================= RESUMEN =================
            var sh0 = wb.createSheet("Resumen");
            int r = 0;

            var a = sh0.createRow(r++);
            a.createCell(0).setCellValue("Proveedor");
            a.createCell(1).setCellValue(prov.getName());

            var b = sh0.createRow(r++);
            b.createCell(0).setCellValue("Saldo/Deuda (COP)");
            b.createCell(1).setCellValue(saldo);

            var c = sh0.createRow(r++);
            c.createCell(0).setCellValue("Estado");
            c.createCell(1).setCellValue(estado);

            sh0.autoSizeColumn(0);
            sh0.autoSizeColumn(1);

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

            // ================= COMPRAS =================
            var sh2 = wb.createSheet("Compras");
            int j = 0;
            var h2 = sh2.createRow(j++);
            h2.createCell(0).setCellValue("Fecha");
            h2.createCell(1).setCellValue("USDT");
            h2.createCell(2).setCellValue("Tasa");
            h2.createCell(3).setCellValue("Pesos");

            for (var bdl : compras) {
                var rr = sh2.createRow(j++);
                rr.createCell(0).setCellValue(bdl.getDate() != null ? bdl.getDate().toString() : "");
                rr.createCell(1).setCellValue(bdl.getAmount() != null ? bdl.getAmount() : 0.0);
                rr.createCell(2).setCellValue(bdl.getTasa() != null ? bdl.getTasa() : 0.0);
                double pesos = bdl.getPesos() != null ? bdl.getPesos()
                        : ((bdl.getAmount() != null && bdl.getTasa() != null) ? bdl.getAmount() * bdl.getTasa() : 0.0);
                rr.createCell(3).setCellValue(pesos);
            }
            for (int col = 0; col <= 3; col++) sh2.autoSizeColumn(col);

            // ================= VENTAS =================
            var sh3 = wb.createSheet("Ventas");
            int k = 0;
            var h3 = sh3.createRow(k++);
            h3.createCell(0).setCellValue("Fecha");
            h3.createCell(1).setCellValue("USDT");
            h3.createCell(2).setCellValue("Tasa");
            h3.createCell(3).setCellValue("Pesos");

            for (var sdl : ventas) {
                var rr = sh3.createRow(k++);
                rr.createCell(0).setCellValue(sdl.getDate() != null ? sdl.getDate().toString() : "");
                rr.createCell(1).setCellValue(sdl.getDollars() != null ? sdl.getDollars() : 0.0);
                rr.createCell(2).setCellValue(sdl.getTasa() != null ? sdl.getTasa() : 0.0);
                double pesos = sdl.getPesos() != null ? sdl.getPesos()
                        : ((sdl.getDollars() != null && sdl.getTasa() != null) ? sdl.getDollars() * sdl.getTasa() : 0.0);
                rr.createCell(3).setCellValue(pesos);
            }
            for (int col = 0; col <= 3; col++) sh3.autoSizeColumn(col);

            // salida
            try (var out = new java.io.ByteArrayOutputStream()) {
                wb.write(out);
                return out.toByteArray();
            }
        }
    }

    private static String nvl(String s) { return s == null ? "" : s; }
}
