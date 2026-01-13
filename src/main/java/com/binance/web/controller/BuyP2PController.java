package com.binance.web.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.binance.web.model.AssignAccountDto;
import com.binance.web.model.BuyP2PDto;
import com.binance.web.service.BuyP2PService;

@RestController
@RequestMapping("/buyP2P")
public class BuyP2PController {

    @Autowired private BuyP2PService buyP2PService;

    @GetMapping("/today/no-asignadas")
    public ResponseEntity<List<BuyP2PDto>> todayNoAsignadas(@RequestParam String account) {
        return ResponseEntity.ok(buyP2PService.getTodayNoAsignadas(account));
    }

    @GetMapping("/today/no-asignadas/all-binance")
    public ResponseEntity<List<BuyP2PDto>> todayNoAsignadasAll() {
        return ResponseEntity.ok(buyP2PService.getTodayNoAsignadasAllAccounts());
    }

    @PostMapping("/assign-account-cop")
    public ResponseEntity<String> assign(@RequestParam Integer buyId,
                                         @RequestBody List<AssignAccountDto> accounts) {
        String msg = buyP2PService.processAssignAccountCop(buyId, accounts);
        return ResponseEntity.ok(msg);
    }
}
