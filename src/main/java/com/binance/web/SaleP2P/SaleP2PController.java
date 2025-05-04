package com.binance.web.SaleP2P;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.binance.web.Entity.SaleP2P;

@RestController
@RequestMapping("/saleP2P")
public class SaleP2PController {
	
	
    private final SaleP2PService saleP2PService;
	
	public SaleP2PController(SaleP2PService saleP2PService) {
	    this.saleP2PService = saleP2PService;
	}
	

    @GetMapping
    public ResponseEntity<List<SaleP2P>> getAllSales() {
        List<SaleP2P> sales = saleP2PService.findAllSaleP2P();
        return ResponseEntity.ok(sales);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SaleP2P> getSaleById(@PathVariable Integer id) {
        SaleP2P saleP2P = saleP2PService.findByIdSaleP2P(id);
        return saleP2P != null ? ResponseEntity.ok(saleP2P) : ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<SaleP2PDto> createSale(@RequestBody SaleP2PDto saleP2PDto) {
        saleP2PService.processAssignAccountCop(saleP2PDto);;
        return ResponseEntity.status(HttpStatus.CREATED).body(saleP2PDto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SaleP2P> updateSale(@PathVariable Integer id, @RequestBody SaleP2P saleP2P) {
        SaleP2P existingSale = saleP2PService.findByIdSaleP2P(id);
        if (existingSale == null) {
            return ResponseEntity.notFound().build();
        }
        saleP2PService.updateSaleP2P(id, saleP2P);
        return ResponseEntity.ok(saleP2P);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSale(@PathVariable Integer id) {
        saleP2PService.deleteSaleP2P(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Devuelve, por cada nameAccount que tenga ventas sin AccountCop asignada,
     * la suma de pesosCop y la suma de commission.
     */
    @GetMapping("/s4")
    public ResponseEntity<List<SaleP2PSummaryDto>> getSummaryByName() {
        // 1) Recupero todas las ventas
        List<SaleP2P> all = saleP2PService.findAllSaleP2P();

        // 2) Filtrar ventas sin AccountCop y con nameAccount no nulo
        Map<String, List<SaleP2P>> grouped = all.stream()
            .filter(sale -> (sale.getAccountCops() == null || sale.getAccountCops().isEmpty())
                         && sale.getNameAccount() != null
                         && !sale.getNameAccount().trim().isEmpty())
            .collect(Collectors.groupingBy(SaleP2P::getNameAccount));

        // 3) Construyo la lista de DTOs de resumen
        List<SaleP2PSummaryDto> summary = grouped.entrySet().stream()
            .map(e -> {
                String name = e.getKey();
                double totalPesos = e.getValue().stream()
                    .mapToDouble(SaleP2P::getPesosCop)
                    .sum();
                double totalComm = e.getValue().stream()
                    .mapToDouble(SaleP2P::getCommission)
                    .sum();
                return new SaleP2PSummaryDto(name, totalPesos, totalComm);
            })
            .collect(Collectors.toList());

        return ResponseEntity.ok(summary);
    }


    // DTO auxiliar para el resumen
    public static class SaleP2PSummaryDto {
        private String nameAccount;
        private Double totalPesosCop;
        private Double totalCommission;

        public SaleP2PSummaryDto(String nameAccount, Double totalPesosCop, Double totalCommission) {
            this.nameAccount     = nameAccount;
            this.totalPesosCop   = totalPesosCop;
            this.totalCommission = totalCommission;
        }
        public String getNameAccount()       { return nameAccount; }
        public Double getTotalPesosCop()     { return totalPesosCop; }
        public Double getTotalCommission()   { return totalCommission; }
    }
}

