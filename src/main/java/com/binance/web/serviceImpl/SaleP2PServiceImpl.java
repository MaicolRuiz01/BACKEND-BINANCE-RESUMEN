package com.binance.web.serviceImpl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.binance.web.BinanceAPI.BinanceService;
import com.binance.web.Entity.AccountBinance;
import com.binance.web.Entity.AccountCop;
import com.binance.web.Entity.SaleP2P;
import com.binance.web.Entity.SaleP2pAccountCop;
import com.binance.web.OrderP2P.OrderP2PService;
import com.binance.web.Repository.AccountBinanceRepository;
import com.binance.web.Repository.P2PAssignmentRuleRepository;
import com.binance.web.Repository.SaleP2PRepository;
import com.binance.web.Repository.SaleP2pAccountCopRepository;
import com.binance.web.model.AssignAccountDto;
import com.binance.web.model.SaleP2PDto;
import com.binance.web.service.AccountBinanceService;
import com.binance.web.service.AccountCopService;
import com.binance.web.service.SaleP2PService;

@Slf4j
@Service
public class SaleP2PServiceImpl implements SaleP2PService {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    @Autowired private SaleP2PRepository saleP2PRepository;
    @Autowired private AccountCopService accountCopService;
    @Autowired private BinanceService binanceService;
    @Autowired private OrderP2PService orderP2PService;
    @Autowired private AccountBinanceRepository accountBinanceRepository;
    @Autowired private AccountBinanceService accountBinanceService;
    @Autowired private SaleP2pAccountCopRepository saleP2pAccountCopRepository;
    @Autowired private P2PAssignmentRuleRepository assignmentRuleRepository;

    @Override
    public List<SaleP2PDto> findAllSaleP2P() {
        return saleP2PRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    public SaleP2P findByIdSaleP2P(Integer id) {
        return saleP2PRepository.findById(id).orElse(null);
    }

    @Override
    public void saveSaleP2P(SaleP2P saleP2P) {
        saleP2PRepository.save(saleP2P);
    }

    /** Bug fix: guardaba el parámetro `sale` en vez de la entidad cargada de la BD. */
    @Override
    public void updateSaleP2P(Integer id, SaleP2P sale) {
        SaleP2P existing = saleP2PRepository.findById(id).orElse(null);
        if (existing == null) {
            log.warn("updateSaleP2P: no existe venta con id {}", id);
            return;
        }
        existing.setDate(sale.getDate());
        existing.setPesosCop(sale.getPesosCop());
        existing.setDollarsUs(sale.getDollarsUs());
        existing.setCommission(sale.getCommission());
        existing.setTasa(sale.getTasa());
        existing.setUtilidad(sale.getUtilidad());
        existing.setAsignado(sale.getAsignado());
        saleP2PRepository.save(existing);
    }

    @Override
    public void deleteSaleP2P(Integer id) {
        saleP2PRepository.deleteById(id);
    }

    /**
     * Solo lee de BD. El sync con Binance lo hace P2PSyncScheduler en background.
     * Para forzar una sync inmediata usar POST /p2p-sync/trigger.
     */
    @Override
    public List<SaleP2PDto> getLastSaleP2pToday(String account) {
        return convertToDtoList(LocalDate.now(ZONE_BOGOTA), account);
    }

    private void createSaleP2pDirectly(String account, LocalDate today) {
        try {
            ZoneId zone = ZONE_BOGOTA;
            long start = today.atStartOfDay(zone).toInstant().toEpochMilli();
            long end   = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

            String jsonResponse = binanceService.getP2POrdersInRange(account, start, end, "SELL");
            JsonNode root = mapper.readTree(jsonResponse);

            if (root.has("error")) {
                log.error("Error Binance ({}): {}", account, root.get("error").asText());
                return;
            }

            LocalDateTime inicio = today.atStartOfDay();
            LocalDateTime fin    = inicio.plusDays(1);

            for (JsonNode obj : root.path("data")) {
                String status    = obj.path("orderStatus").asText("");
                String tradeType = obj.path("tradeType").asText("");
                String asset     = obj.path("asset").asText("");

                if (!"COMPLETED".equalsIgnoreCase(status))   continue;
                if (!"SELL".equalsIgnoreCase(tradeType))     continue;
                if (!"USDT".equalsIgnoreCase(asset))         continue;

                String orderNumber    = obj.path("orderNumber").asText();
                LocalDateTime fechaOrden = convertTimestamp(obj.path("createTime").asLong());

                if (fechaOrden.isBefore(inicio) || !fechaOrden.isBefore(fin)) continue;
                if (saleP2PRepository.existsByNumberOrder(orderNumber))        continue;

                SaleP2P sale = new SaleP2P();
                sale.setNumberOrder(orderNumber);
                sale.setDate(fechaOrden);
                sale.setPesosCop(obj.path("totalPrice").asDouble(0.0));
                sale.setDollarsUs(obj.path("amount").asDouble(0.0));

                double commission = obj.has("takerCommission") && !obj.path("takerCommission").isNull()
                        ? obj.path("takerCommission").asDouble(0.0)
                        : obj.path("commission").asDouble(0.0);
                sale.setCommission(commission);
                sale.setTasa(calculateTasaVenta(sale));
                sale = assignAccountBinance(sale, account);
                sale.setAsignado(false);
                sale.setUtilidad(0.0);
                saveSaleP2P(sale);
                autoAssignIfRuleExists(sale);
            }
        } catch (Exception e) {
            log.error("Error en createSaleP2pDirectly ({}): {}", account, e.getMessage(), e);
        }
    }

    private LocalDateTime convertTimestamp(long timestamp) {
        return Instant.ofEpochMilli(timestamp).atZone(ZONE_BOGOTA).toLocalDateTime();
    }

    private Double calculateTasaVenta(SaleP2P sale) {
        if (sale.getDollarsUs() == null || sale.getDollarsUs() == 0.0) return 0.0;
        return sale.getPesosCop() / sale.getDollarsUs();
    }

    private Double generateTax(SaleP2P sale) {
        double impuesto = 0.0;
        List<SaleP2pAccountCop> accountCop = sale.getAccountCopsDetails();
        for (SaleP2pAccountCop account : accountCop) {
            if (account.getAccountCop() == null) {
                impuesto += account.getAmount() * 0.004;
            }
        }
        return impuesto;
    }

    public void saveUtilitydefinitive(List<SaleP2P> rangeSales, Double averageRate) {
        for (SaleP2P sale : rangeSales) {
            double utilidad = sale.getPesosCop() - ((sale.getDollarsUs() + sale.getCommission()) * averageRate);
            utilidad -= generateTax(sale);
            sale.setUtilidad(utilidad);
            saleP2PRepository.save(sale);
        }
    }

    @Override
    @Transactional
    public String processAssignAccountCop(Integer saleId, List<AssignAccountDto> accounts) {
        SaleP2P sale = saleP2PRepository.findById(saleId).orElse(null);
        if (sale == null) return "No se encontró la venta con id " + saleId;
        if (Boolean.TRUE.equals(sale.getAsignado())) return "Esta venta ya está asignada.";

        if (sale.getAccountCopsDetails() == null) sale.setAccountCopsDetails(new ArrayList<>());

        // Revertir efectos anteriores
        for (SaleP2pAccountCop prev : sale.getAccountCopsDetails()) {
            if (prev.getAccountCop() != null) {
                AccountCop acc = accountCopService.findByIdAccountCop(prev.getAccountCop().getId());
                if (acc != null) {
                    double amt = prev.getAmount() != null ? prev.getAmount() : 0.0;
                    acc.setBalance((acc.getBalance() != null ? acc.getBalance() : 0.0) - amt);
                    acc.setCupoDisponibleHoy((acc.getCupoDisponibleHoy() != null ? acc.getCupoDisponibleHoy() : 0.0) + amt);
                    accountCopService.saveAccountCopSafe(acc);
                }
            }
        }

        sale.getAccountCopsDetails().clear();

        for (AssignAccountDto dto : accounts) {
            SaleP2pAccountCop detail = new SaleP2pAccountCop();
            detail.setSaleP2p(sale);
            detail.setAmount(dto.getAmount());
            detail.setNameAccount(dto.getNameAccount());

            if (dto.getAccountCop() != null) {
                AccountCop acc = accountCopService.findByIdAccountCop(dto.getAccountCop());
                detail.setAccountCop(acc);
                double amt = dto.getAmount() != null ? dto.getAmount() : 0.0;
                acc.setBalance((acc.getBalance() != null ? acc.getBalance() : 0.0) + amt);
                acc.setCupoDisponibleHoy((acc.getCupoDisponibleHoy() != null ? acc.getCupoDisponibleHoy() : 0.0) - amt);
                accountCopService.saveAccountCopSafe(acc);
            }
            sale.getAccountCopsDetails().add(detail);
        }

        accountBinanceService.subtractBalance(sale.getBinanceAccount().getName(), sale.getDollarsUs());
        if (sale.getUtilidad() == null) sale.setUtilidad(0.0);
        sale.setUtilidad(sale.getUtilidad() + generateTax(sale));
        sale.setAsignado(true);
        saleP2PRepository.save(sale);
        return "Asignación realizada con éxito";
    }

    @Transactional
    public SaleP2P assignAccountCop(List<AssignAccountDto> accounts, SaleP2P sale) {
        if (sale.getAccountCopsDetails() != null && !sale.getAccountCopsDetails().isEmpty()) {
            for (SaleP2pAccountCop prev : sale.getAccountCopsDetails()) {
                if (prev.getAccountCop() != null) {
                    AccountCop acc = accountCopService.findByIdAccountCop(prev.getAccountCop().getId());
                    if (acc != null) {
                        double amt = prev.getAmount() != null ? prev.getAmount() : 0.0;
                        acc.setBalance((acc.getBalance() != null ? acc.getBalance() : 0.0) - amt);
                        acc.setCupoDisponibleHoy((acc.getCupoDisponibleHoy() != null ? acc.getCupoDisponibleHoy() : 0.0) + amt);
                        accountCopService.saveAccountCopSafe(acc);
                    }
                }
            }
            sale.getAccountCopsDetails().forEach(d -> d.setSaleP2p(null));
            sale.getAccountCopsDetails().clear();
        } else {
            sale.setAccountCopsDetails(new ArrayList<>());
        }

        for (AssignAccountDto dto : accounts) {
            SaleP2pAccountCop detail = new SaleP2pAccountCop();
            detail.setSaleP2p(sale);
            detail.setAmount(dto.getAmount());
            detail.setNameAccount(dto.getNameAccount());
            if (dto.getAccountCop() != null) {
                AccountCop acc = accountCopService.findByIdAccountCop(dto.getAccountCop());
                detail.setAccountCop(acc);
                double amt = dto.getAmount() != null ? dto.getAmount() : 0.0;
                acc.setBalance((acc.getBalance() != null ? acc.getBalance() : 0.0) + amt);
                acc.setCupoDisponibleHoy((acc.getCupoDisponibleHoy() != null ? acc.getCupoDisponibleHoy() : 0.0) - amt);
                accountCopService.saveAccountCopSafe(acc);
            }
            sale.getAccountCopsDetails().add(detail);
        }
        return sale;
    }

    private SaleP2P assignAccountBinance(SaleP2P sale, String name) {
        AccountBinance accountBinance = accountBinanceRepository.findByName(name);
        sale.setBinanceAccount(accountBinance);
        return sale;
    }

    private List<SaleP2PDto> convertToDtoList(LocalDate today, String name) {
        AccountBinance accountBinance = accountBinanceRepository.findByName(name);
        if (accountBinance == null) return List.of();

        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end   = start.plusDays(1);

        return saleP2PRepository
                .findByAccountAndDateBetween(accountBinance.getId(), start, end)
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private SaleP2PDto convertToDto(SaleP2P sale) {
        SaleP2PDto dto = new SaleP2PDto();
        dto.setId(sale.getId());
        dto.setNumberOrder(sale.getNumberOrder());
        dto.setDate(sale.getDate());
        dto.setCommission(sale.getCommission());
        dto.setPesosCop(sale.getPesosCop());
        dto.setDollarsUs(sale.getDollarsUs());
        dto.setTasa(sale.getTasa());
        dto.setAsignado(sale.getAsignado());
        dto.setNameAccountBinance(sale.getBinanceAccount() != null ? sale.getBinanceAccount().getName() : null);
        if (sale.getAccountCopsDetails() != null) {
            dto.setAccountCopsDetails(sale.getAccountCopsDetails().stream()
                    .map(d -> new com.binance.web.model.SaleP2PDto.AccountCopDetailDto(d.getNameAccount(), d.getAmount()))
                    .collect(java.util.stream.Collectors.toList()));
        }
        return dto;
    }

    @Override
    public List<SaleP2P> obtenerVentasPorFecha(LocalDate fecha) {
        LocalDateTime start = fecha.atStartOfDay();
        return saleP2PRepository.findByDateBetween(start, start.plusDays(1));
    }

    @Override
    public Double obtenerComisionesPorFecha(LocalDate fecha) {
        LocalDateTime start = fecha.atStartOfDay();
        return saleP2PRepository.findByDateBetween(start, start.plusDays(1)).stream()
                .mapToDouble(SaleP2P::getCommission)
                .sum();
    }

    /** Bug fix: antes devolvía null. Ahora delega al repositorio. */
    @Override
    public List<SaleP2P> obtenerVentasEntreFechas(LocalDateTime inicio, LocalDateTime fin) {
        return saleP2PRepository.findByDateBetween(inicio, fin);
    }

    @Override
    public String getAllP2PFromBinance(String account, LocalDate from, LocalDate to) {
        ZoneId zone = ZONE_BOGOTA;
        long start  = from.atStartOfDay(zone).toInstant().toEpochMilli();
        long end    = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        return binanceService.getP2POrdersInRange(account, start, end, null);
    }

    /** Solo lee de BD. Sync en background vía P2PSyncScheduler. */
    @Override
    public List<SaleP2PDto> getTodayNoAsignadas(String account) {
        LocalDate today     = LocalDate.now(ZONE_BOGOTA);
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end   = start.plusDays(1);
        AccountBinance ab   = accountBinanceRepository.findByName(account);
        if (ab == null) return List.of();
        return saleP2PRepository.findNoAsignadasByAccountAndDateBetween(ab.getId(), start, end)
                .stream().map(this::convertToDto).collect(Collectors.toList());
    }

    private void importSalesP2PRange(String account, LocalDate from, LocalDate to) {
        try {
            ZoneId zone = ZONE_BOGOTA;
            long start  = from.atStartOfDay(zone).toInstant().toEpochMilli();
            long end    = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

            String jsonResponse = binanceService.getP2POrdersInRange(account, start, end, "SELL");
            JsonNode root = mapper.readTree(jsonResponse);

            if (root.has("error")) {
                log.error("Error Binance ({}): {}", account, root.get("error").asText());
                return;
            }

            JsonNode dataArray = root.path("data");
            if (!dataArray.isArray() || dataArray.isEmpty()) return;

            for (JsonNode obj : dataArray) {
                String status    = obj.path("orderStatus").asText("");
                String tradeType = obj.path("tradeType").asText("");
                String asset     = obj.path("asset").asText("");

                if (!"COMPLETED".equalsIgnoreCase(status))  continue;
                if (!"SELL".equalsIgnoreCase(tradeType))    continue;
                if (!"USDT".equalsIgnoreCase(asset))        continue;

                String orderNumber = obj.path("orderNumber").asText();
                if (orderNumber.isBlank())                               continue;
                if (saleP2PRepository.existsByNumberOrder(orderNumber)) continue;

                LocalDateTime fechaOrden = obj.has("createTime")
                        ? convertTimestamp(obj.path("createTime").asLong())
                        : LocalDateTime.now(zone);

                SaleP2P sale = new SaleP2P();
                sale.setNumberOrder(orderNumber);
                sale.setDate(fechaOrden);
                sale.setPesosCop(obj.path("totalPrice").asDouble(0.0));
                sale.setDollarsUs(obj.path("amount").asDouble(0.0));

                double commission = !obj.path("takerCommission").isNull()
                        ? obj.path("takerCommission").asDouble(0.0)
                        : obj.path("commission").asDouble(0.0);
                sale.setCommission(commission);
                sale.setTasa(calculateTasaVenta(sale));
                sale = assignAccountBinance(sale, account);
                sale.setAsignado(false);
                sale.setUtilidad(0.0);
                saveSaleP2P(sale);
                autoAssignIfRuleExists(sale);
            }
        } catch (Exception e) {
            log.error("Error importando rango P2P ({}): {}", account, e.getMessage(), e);
        }
    }

    /** Solo lee de BD para todas las cuentas. Sync en background vía P2PSyncScheduler. */
    @Override
    public List<SaleP2PDto> getTodayNoAsignadasAllAccounts() {
        List<SaleP2PDto> out = new ArrayList<>();
        for (AccountBinance acc : accountBinanceRepository.findByTipo("BINANCE")) {
            saleP2PRepository.findNoAsignadasGeneralByAccount(acc.getId())
                    .forEach(s -> out.add(convertToDto(s)));
        }
        return out;
    }

    // ─────────────────────────────────────────────────────────────
    // Auto-asignación por regla activa
    // ─────────────────────────────────────────────────────────────

    /**
     * Si existe una regla activa para la cuenta Binance de esta venta,
     * la asigna automáticamente a la cuenta COP configurada.
     * Si no hay regla, la venta queda sin asignar (modo manual).
     */
    private void autoAssignIfRuleExists(SaleP2P sale) {
        if (sale.getBinanceAccount() == null) return;
        String binanceName = sale.getBinanceAccount().getName();

        assignmentRuleRepository.findByBinanceAccount_Name(binanceName)
                .filter(r -> Boolean.TRUE.equals(r.getActive()))
                .ifPresent(rule -> {
                    AccountCop cop = rule.getCopAccount();
                    double amount  = sale.getPesosCop() != null ? sale.getPesosCop() : 0.0;

                    // Detalle de asignación
                    SaleP2pAccountCop detail = new SaleP2pAccountCop();
                    detail.setSaleP2p(sale);
                    detail.setAmount(amount);
                    detail.setNameAccount(cop.getName());
                    detail.setAccountCop(cop);

                    // Actualizar saldo COP
                    cop.setBalance((cop.getBalance() != null ? cop.getBalance() : 0.0) + amount);
                    cop.setCupoDisponibleHoy(
                            (cop.getCupoDisponibleHoy() != null ? cop.getCupoDisponibleHoy() : 0.0) - amount);
                    accountCopService.saveAccountCopSafe(cop);

                    // Descontar USDT del saldo Binance
                    if (sale.getDollarsUs() != null && sale.getDollarsUs() > 0) {
                        accountBinanceService.subtractBalance(binanceName, sale.getDollarsUs());
                    }

                    // Marcar venta como asignada
                    if (sale.getAccountCopsDetails() == null) sale.setAccountCopsDetails(new ArrayList<>());
                    sale.getAccountCopsDetails().add(detail);
                    sale.setAsignado(true);
                    saleP2PRepository.save(sale);

                    log.info("Venta {} auto-asignada a {} ({} COP)", sale.getNumberOrder(), cop.getName(), amount);
                });
    }

    // ─────────────────────────────────────────────────────────────
    // Reasignación puntual (con reversión)
    // ─────────────────────────────────────────────────────────────

    /**
     * Cambia la cuenta COP de una venta ya asignada.
     * Revierte el balance/cupo de la cuenta anterior y aplica en la nueva.
     * El saldo USDT en Binance NO se vuelve a tocar (ya se descontó al asignar).
     */
    @Override
    @Transactional
    public String reassignSale(Integer saleId, Integer newCopAccountId) {
        SaleP2P sale = saleP2PRepository.findById(saleId).orElse(null);
        if (sale == null)                               return "Venta no encontrada: " + saleId;
        if (!Boolean.TRUE.equals(sale.getAsignado()))   return "La venta " + saleId + " no está asignada aún.";

        AccountCop newCop = accountCopService.findByIdAccountCop(newCopAccountId);
        if (newCop == null) return "Cuenta COP no encontrada: " + newCopAccountId;

        double amount = sale.getPesosCop() != null ? sale.getPesosCop() : 0.0;

        // 1) Revertir asignaciones anteriores
        if (sale.getAccountCopsDetails() != null) {
            for (SaleP2pAccountCop prev : sale.getAccountCopsDetails()) {
                if (prev.getAccountCop() != null) {
                    AccountCop old = accountCopService.findByIdAccountCop(prev.getAccountCop().getId());
                    if (old != null) {
                        double oldAmt = prev.getAmount() != null ? prev.getAmount() : 0.0;
                        old.setBalance((old.getBalance() != null ? old.getBalance() : 0.0) - oldAmt);
                        old.setCupoDisponibleHoy(
                                (old.getCupoDisponibleHoy() != null ? old.getCupoDisponibleHoy() : 0.0) + oldAmt);
                        accountCopService.saveAccountCopSafe(old);
                        log.info("Revertido: {} COP de {} en {}", oldAmt, old.getName(), saleId);
                    }
                }
            }
            sale.getAccountCopsDetails().clear();
        } else {
            sale.setAccountCopsDetails(new ArrayList<>());
        }

        // 2) Aplicar en nueva cuenta COP
        SaleP2pAccountCop newDetail = new SaleP2pAccountCop();
        newDetail.setSaleP2p(sale);
        newDetail.setAmount(amount);
        newDetail.setNameAccount(newCop.getName());
        newDetail.setAccountCop(newCop);

        newCop.setBalance((newCop.getBalance() != null ? newCop.getBalance() : 0.0) + amount);
        newCop.setCupoDisponibleHoy(
                (newCop.getCupoDisponibleHoy() != null ? newCop.getCupoDisponibleHoy() : 0.0) - amount);
        accountCopService.saveAccountCopSafe(newCop);

        sale.getAccountCopsDetails().add(newDetail);
        saleP2PRepository.save(sale);

        log.info("Venta {} reasignada → {} ({} COP)", saleId, newCop.getName(), amount);
        return "Reasignación exitosa: " + amount + " COP → " + newCop.getName();
    }

    @Override
    @Transactional
    public String fixDuplicateAssignmentsAuto(Integer saleP2pId) {
        List<SaleP2pAccountCop> details = saleP2pAccountCopRepository.findBySaleP2p_Id(saleP2pId);
        if (details == null || details.size() <= 1) {
            return "OK: La venta " + saleP2pId + " no tiene duplicados.";
        }

        Map<String, List<SaleP2pAccountCop>> grouped = new HashMap<>();
        for (SaleP2pAccountCop d : details) {
            String accKey = (d.getAccountCop() != null)
                    ? "ID:" + d.getAccountCop().getId()
                    : "NAME:" + (d.getNameAccount() == null ? "" : d.getNameAccount().trim().toUpperCase());
            String key = accKey + "|AMT:" + (d.getAmount() != null ? d.getAmount() : 0.0);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        }

        int removed = 0;
        for (List<SaleP2pAccountCop> list : grouped.values()) {
            if (list.size() <= 1) continue;
            list.sort(Comparator.comparing(SaleP2pAccountCop::getId));
            for (int i = 1; i < list.size(); i++) {
                SaleP2pAccountCop dup = list.get(i);
                if (dup.getAccountCop() != null) {
                    AccountCop acc = accountCopService.findByIdAccountCop(dup.getAccountCop().getId());
                    if (acc != null) {
                        double amt = dup.getAmount() != null ? dup.getAmount() : 0.0;
                        acc.setBalance((acc.getBalance() != null ? acc.getBalance() : 0.0) - amt);
                        acc.setCupoDisponibleHoy((acc.getCupoDisponibleHoy() != null ? acc.getCupoDisponibleHoy() : 0.0) + amt);
                        accountCopService.saveAccountCopSafe(acc);
                    }
                }
                saleP2pAccountCopRepository.deleteById(dup.getId());
                removed++;
            }
        }

        if (removed == 0) return "OK: No se detectaron duplicados exactos en la venta " + saleP2pId + ".";
        return "Arreglado: se eliminaron " + removed + " asignación(es) duplicada(s) y se revirtió balance/cupo.";
    }
}
