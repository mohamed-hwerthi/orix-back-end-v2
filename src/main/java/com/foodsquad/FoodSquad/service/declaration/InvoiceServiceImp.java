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

    public byte[] generateInvoice(String orderId) throws Exception {
        JasperReport jasperReport = getCachedInvoiceReport();

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Calculate totals and tax details
        double totalHT = 0;
        double totalTTC = 0;
        Map<Tax, Double> taxDetails = new HashMap<>();

        // Prepare items with HT and TTC prices
        List<Map<String, Object>> items = new ArrayList<>();

        for (Map.Entry<MenuItem, Integer> entry : order.getMenuItemsWithQuantity().entrySet()) {
            MenuItem item = entry.getKey();
            int quantity = entry.getValue();
            double itemTTCUnit = item.getPrice(); // stored TTC
            double taxRate = item.getTax() != null ? item.getTax().getRate() : 0.0;

            // Calculate HT from TTC
            double itemHTUnit = itemTTCUnit / (1 + (taxRate / 100));
            double itemHT = itemHTUnit * quantity;
            double itemTTC = itemTTCUnit * quantity;
            double taxAmount = itemTTC - itemHT;

            // ROUND to 2 decimals using BigDecimal
            itemHTUnit = round(itemHTUnit);
            itemHT = round(itemHT);
            itemTTC = round(itemTTC);
            taxAmount = round(taxAmount);

            // Accumulate tax
            if (item.getTax() != null) {
                taxDetails.merge(item.getTax(), taxAmount, Double::sum);
            }

            totalHT += itemHT;
            totalTTC += itemTTC;

            Map<String, Object> itemData = new HashMap<>();
            itemData.put("menuItemName", item.getTitle());
            itemData.put("quantity", quantity);
            itemData.put("priceHT", itemHTUnit);
            itemData.put("priceTTC", itemTTCUnit);
            itemData.put("totalHT", itemHT);
            itemData.put("totalTTC", itemTTC);
            items.add(itemData);
        }

        // Apply discount from the order (authoritative)
        double discountTTC = order.getDiscountAmount() != null ? order.getDiscountAmount() : 0.0;
        boolean hasDiscount = discountTTC > 0;
        double discountHT = 0.0;
        if (hasDiscount && totalTTC > 0) {
            // Pro-rata HT based on the same ratio as catalog HT/TTC
            discountHT = round(discountTTC * (totalHT / totalTTC));
        }

        double subtotalHT = round(totalHT);
        double subtotalTTC = round(totalTTC);
        double netTTC = round(subtotalTTC - discountTTC);

        // Get the timbre fiscal amount from database
        Timbre timbre = timbreRepository.findAll().stream().findFirst().orElse(null);
        double timbreAmount = (timbre != null) ? timbre.getAmount() : 0.0;

        // Check if we need to add timbre fiscal (when TTC >= 1 DT) — base on net amount after discount
        boolean addTimbre = (netTTC >= 1.0);
        double finalTTC = addTimbre ? round(netTTC + timbreAmount) : netTTC;

        // Prepare parameters
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("orderId", order.getId());
        parameters.put("totalHT", subtotalHT);
        parameters.put("totalTTC", finalTTC);
        parameters.put("subtotalTTC", subtotalTTC);
        parameters.put("discountTTC", round(discountTTC));
        parameters.put("hasDiscount", hasDiscount);
        parameters.put("createdOn", order.getCreatedOn());
        parameters.put("taxDetails", new ArrayList<>(taxDetails.entrySet()));
        parameters.put("addTimbre", addTimbre);
        parameters.put("timbreAmount", timbreAmount);
        parameters.put("net.sf.jasperreports.resource.path", "images");
        JRDataSource dataSource = new JRBeanCollectionDataSource(items);
        JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);
        return JasperExportManager.exportReportToPdf(jasperPrint);
    }

    private double round(double value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
