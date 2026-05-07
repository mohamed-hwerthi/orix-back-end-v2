package com.foodsquad.FoodSquad.service.impl;

import com.foodsquad.FoodSquad.model.dto.CashSessionSummaryDTO;
import com.foodsquad.FoodSquad.model.entity.CashSession;
import com.foodsquad.FoodSquad.repository.CashSessionRepository;
import jakarta.persistence.EntityNotFoundException;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ZReportService {

    private final CashSessionRepository cashSessionRepository;
    private final CashSessionService cashSessionService;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private volatile JasperReport cachedReport;
    private final Object reportLock = new Object();

    public ZReportService(CashSessionRepository cashSessionRepository, CashSessionService cashSessionService) {
        this.cashSessionRepository = cashSessionRepository;
        this.cashSessionService = cashSessionService;
    }

    @jakarta.annotation.PostConstruct
    public void warmupTemplate() {
        try { getCachedReport(); } catch (Exception ignored) {}
    }

    private JasperReport getCachedReport() throws JRException {
        JasperReport local = cachedReport;
        if (local == null) {
            synchronized (reportLock) {
                local = cachedReport;
                if (local == null) {
                    InputStream stream = getClass().getResourceAsStream("/jasper/z-report.jrxml");
                    local = JasperCompileManager.compileReport(stream);
                    cachedReport = local;
                }
            }
        }
        return local;
    }

    public byte[] generateZReport(Long sessionId) throws JRException {
        CashSession s = cashSessionRepository.findById(sessionId)
                .orElseThrow(() -> new EntityNotFoundException("Session not found: " + sessionId));
        CashSessionSummaryDTO summary = cashSessionService.summary(sessionId);

        JasperReport report = getCachedReport();

        Map<String, Object> params = new HashMap<>();
        params.put("zNumber", s.getZReportNumber() != null ? s.getZReportNumber() : "BROUILLON");
        params.put("openedAt", s.getOpenedAt() != null ? s.getOpenedAt().format(FMT) : "");
        params.put("closedAt", s.getClosedAt() != null ? s.getClosedAt().format(FMT) : "—");
        params.put("cashier", s.getOpenedBy() != null ? s.getOpenedBy().getEmail() : "");
        params.put("closedBy", s.getClosedBy() != null ? s.getClosedBy().getEmail() : "");
        params.put("openingAmount", nz(s.getOpeningAmount()));

        params.put("ordersCount", summary.getOrdersCount());
        params.put("unitsSold", summary.getUnitsSold());
        params.put("totalRevenue", summary.getTotalRevenue());
        params.put("totalDiscount", summary.getTotalDiscount());
        params.put("cashSales", summary.getCashSales());
        params.put("cardSales", summary.getCardSales());
        params.put("mixedSales", summary.getMixedSales());
        params.put("otherSales", summary.getOtherSales());
        params.put("refundsCount", summary.getRefundsCount());
        params.put("refundsAmount", summary.getRefundsAmount());
        params.put("cashIn", summary.getCashIn());
        params.put("cashOut", summary.getCashOut());

        params.put("expectedCash", nz(s.getExpectedCash()));
        params.put("countedCash", nz(s.getCountedCash()));
        params.put("cashVariance", nz(s.getCashVariance()));
        params.put("expectedCard", nz(s.getExpectedCard()));
        params.put("countedCard", nz(s.getCountedCard()));
        params.put("cardVariance", nz(s.getCardVariance()));
        params.put("closingNotes", s.getClosingNotes());

        // Top products as data source
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (CashSessionSummaryDTO.TopProduct tp : summary.getTopProducts()) {
            Map<String, Object> row = new HashMap<>();
            row.put("title", tp.title);
            row.put("quantitySold", tp.quantitySold);
            row.put("revenue", tp.revenue);
            rows.add(row);
        }
        JRBeanCollectionDataSource ds = new JRBeanCollectionDataSource(rows);
        JasperPrint print = JasperFillManager.fillReport(report, params, ds);
        return JasperExportManager.exportReportToPdf(print);
    }

    private double nz(Double v) {
        return v == null ? 0.0 : v;
    }
}
