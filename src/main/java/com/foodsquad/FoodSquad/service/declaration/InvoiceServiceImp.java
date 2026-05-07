package com.foodsquad.FoodSquad.service.declaration;

import com.foodsquad.FoodSquad.model.dto.MenuItemEntry;
import com.foodsquad.FoodSquad.model.entity.MenuItem;
import com.foodsquad.FoodSquad.model.entity.Order;
import com.foodsquad.FoodSquad.model.entity.Tax;
import com.foodsquad.FoodSquad.model.entity.Timbre;
import com.foodsquad.FoodSquad.repository.OrderRepository;
import com.foodsquad.FoodSquad.repository.TimbreRepository;
import com.foodsquad.FoodSquad.service.impl.OrderService;
import jakarta.persistence.EntityNotFoundException;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service

public class InvoiceServiceImp implements InvoiceService {

    private final OrderService orderService;

    private final OrderRepository orderRepository;

    private final TimbreRepository timbreRepository;
    private final String invoice_template_path = "/jasper/invoice.jrxml";

    // Cache of compiled Jasper report — compile once, reuse across requests
    private volatile JasperReport cachedInvoiceReport;
    private final Object reportLock = new Object();

    // Cache of the timbre fiscal — rarely changes, was being fetched on every invoice
    private volatile Double cachedTimbreAmount;
    private volatile long timbreCacheLoadedAt = 0L;
    private static final long TIMBRE_CACHE_TTL_MS = 60_000L; // 1 minute


    Logger log = LoggerFactory.getLogger(InvoiceService.class);


    public InvoiceServiceImp(OrderService orderService, OrderRepository orderRepository, TimbreRepository timbreRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
        this.timbreRepository = timbreRepository;
    }

    /** Pré-compile le template Jasper au démarrage de l'app (gain ~1-3s sur la 1ère facture). */
    @jakarta.annotation.PostConstruct
    public void warmupTemplate() {
        try {
            getCachedInvoiceReport();
        } catch (Exception e) {
            log.warn("Impossible de pré-compiler le template invoice : {}", e.getMessage());
        }
    }

    private JasperReport getCachedInvoiceReport() throws JRException {
        JasperReport local = cachedInvoiceReport;
        if (local == null) {
            synchronized (reportLock) {
                local = cachedInvoiceReport;
                if (local == null) {
                    InputStream reportStream = getClass().getResourceAsStream(invoice_template_path);
                    local = JasperCompileManager.compileReport(reportStream);
                    cachedInvoiceReport = local;
                    log.info("Invoice Jasper template compiled and cached");
                }
            }
        }
        return local;
    }





    private Order fetchOrder(String orderId) {
        return orderService.getSimpleOrderById(orderId);
    }

    private File createTempPdfFile() throws IOException {
        return File.createTempFile("my-invoice", ".pdf");
    }

    private void generateInvoicePdf(Order orderDTO, Locale localKey, File pdfFile) throws JRException, IOException {
        try (FileOutputStream pos = new FileOutputStream(pdfFile)) {
            JasperReport report = loadTemplate();
            Map<String, Object> parameters = createParameters(orderDTO, localKey);
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(Collections.singletonList(orderDTO));
            JasperPrint jasperPrint = JasperFillManager.fillReport(report, parameters, dataSource);
            JasperExportManager.exportReportToPdfStream(jasperPrint, pos);
        }
    }

    private JasperReport loadTemplate() throws JRException {
        log.info(String.format("Invoice template path : %s", invoice_template_path));
        final InputStream reportInputStream = getClass().getResourceAsStream(invoice_template_path);
        final JasperDesign jasperDesign = JRXmlLoader.load(reportInputStream);
        return JasperCompileManager.compileReport(jasperDesign);
    }

    private Map<String, Object> createParameters(Order order, Locale localeKey) {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("order", order);
        parameters.put("REPORT_LOCALE", localeKey);
        parameters.put("ClientName", "Jean Dupont");
        parameters.put("InvoiceNumber", "F12345");
        parameters.put("InvoiceDate", new Date());
        parameters.put("TotalAmount", 125.00);
        return parameters;
    }

    /**
     * Flattens a map of menu items and their associated quantities into a list of {@link MenuItemEntry} objects.
     * Each {@link MenuItemEntry} contains the title, description, price, quantity, and image URL of a menu item.
     *
     * @param menuItemsWithQuantity a map where the key is a {@link MenuItem} and the value is the quantity of that menu item.
     * @return a list of {@link MenuItemEntry} objects, each representing a menu item with its corresponding quantity.
     */
    public List<MenuItemEntry> flattenMenuItems(Map<MenuItem, Integer> menuItemsWithQuantity) {
        List<MenuItemEntry> entries = new ArrayList<>();
        for (Map.Entry<MenuItem, Integer> entry : menuItemsWithQuantity.entrySet()) {
            MenuItem menuItem = entry.getKey();
            Integer quantity = entry.getValue();
            MenuItemEntry menuItemEntry = new MenuItemEntry(
                    menuItem.getTitle(),
                    menuItem.getDescription(),
                    menuItem.getPrice(),
                    quantity
            );

            entries.add(menuItemEntry);
        }
        return entries;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public byte[] generateInvoice(String orderId) throws Exception {
        JasperReport jasperReport = getCachedInvoiceReport();

        // Use a transactional read so lazy collections (menuItemsWithQuantity → MenuItem.tax)
        // resolve in a single open session instead of throwing or triggering N+1 queries.
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        Map<MenuItem, Integer> miq = order.getMenuItemsWithQuantity();
        int itemCount = miq.size();

        double totalHT = 0;
        double totalTTC = 0;
        // Pre-sized to avoid rehashing
        List<Map<String, Object>> items = new ArrayList<>(itemCount);

        for (Map.Entry<MenuItem, Integer> entry : miq.entrySet()) {
            MenuItem item = entry.getKey();
            int quantity = entry.getValue();
            double itemTTCUnit = item.getPrice();
            double taxRate = item.getTax() != null ? item.getTax().getRate() : 0.0;

            double itemHTUnit = itemTTCUnit / (1 + (taxRate / 100));
            double itemHT = itemHTUnit * quantity;
            double itemTTC = itemTTCUnit * quantity;

            itemHTUnit = round(itemHTUnit);
            itemHT = round(itemHT);
            itemTTC = round(itemTTC);

            totalHT += itemHT;
            totalTTC += itemTTC;

            Map<String, Object> itemData = new HashMap<>(8);
            itemData.put("menuItemName", item.getTitle());
            itemData.put("quantity", quantity);
            itemData.put("priceHT", itemHTUnit);
            itemData.put("priceTTC", itemTTCUnit);
            itemData.put("totalHT", itemHT);
            itemData.put("totalTTC", itemTTC);
            items.add(itemData);
        }

        double discountTTC = order.getDiscountAmount() != null ? order.getDiscountAmount() : 0.0;
        boolean hasDiscount = discountTTC > 0;

        double subtotalHT = round(totalHT);
        double subtotalTTC = round(totalTTC);
        double netTTC = round(subtotalTTC - discountTTC);

        double timbreAmount = getCachedTimbreAmount();
        boolean addTimbre = (netTTC >= 1.0);
        double finalTTC = addTimbre ? round(netTTC + timbreAmount) : netTTC;

        Map<String, Object> parameters = new HashMap<>(16);
        parameters.put("orderId", order.getId());
        parameters.put("totalHT", subtotalHT);
        parameters.put("totalTTC", finalTTC);
        parameters.put("subtotalTTC", subtotalTTC);
        parameters.put("discountTTC", round(discountTTC));
        parameters.put("hasDiscount", hasDiscount);
        parameters.put("createdOn", order.getCreatedOn());
        parameters.put("addTimbre", addTimbre);
        parameters.put("timbreAmount", timbreAmount);
        parameters.put("net.sf.jasperreports.resource.path", "images");

        JRDataSource dataSource = new JRBeanCollectionDataSource(items);
        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

        // Stream directly into a sized buffer — avoids extra byte[] copies done by exportReportToPdf
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream(32 * 1024);
        JasperExportManager.exportReportToPdfStream(jasperPrint, baos);
        return baos.toByteArray();
    }

    /** Read the timbre once per minute instead of on every invoice. */
    private double getCachedTimbreAmount() {
        long now = System.currentTimeMillis();
        Double cached = cachedTimbreAmount;
        if (cached != null && (now - timbreCacheLoadedAt) < TIMBRE_CACHE_TTL_MS) {
            return cached;
        }
        Timbre timbre = timbreRepository.findAll().stream().findFirst().orElse(null);
        double amount = (timbre != null) ? timbre.getAmount() : 0.0;
        cachedTimbreAmount = amount;
        timbreCacheLoadedAt = now;
        return amount;
    }

    private double round(double value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
