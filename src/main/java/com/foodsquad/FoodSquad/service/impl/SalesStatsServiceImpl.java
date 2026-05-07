package com.foodsquad.FoodSquad.service.impl;

import com.foodsquad.FoodSquad.model.dto.DailySalesDTO;
import com.foodsquad.FoodSquad.model.dto.SalesStatsDTO;
import com.foodsquad.FoodSquad.model.dto.TopProductDTO;
import com.foodsquad.FoodSquad.model.entity.MenuItem;
import com.foodsquad.FoodSquad.model.entity.Order;
import com.foodsquad.FoodSquad.repository.OrderRepository;
import com.foodsquad.FoodSquad.service.declaration.SalesStatsService;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
public class SalesStatsServiceImpl implements SalesStatsService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final OrderRepository orderRepository;

    public SalesStatsServiceImpl(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Override
    public SalesStatsDTO getStats(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(23, 59, 59);

        List<Object[]> rows = orderRepository.aggregateDaily(fromDt, toDt);

        List<DailySalesDTO> daily = rows.stream().map(r -> {
            LocalDate day = ((Date) r[0]).toLocalDate();
            long count = ((Number) r[1]).longValue();
            double revenue = ((Number) r[2]).doubleValue();
            return new DailySalesDTO(day, count, round2(revenue));
        }).toList();

        double totalRevenue = round2(daily.stream().mapToDouble(DailySalesDTO::getRevenue).sum());
        long totalOrders = daily.stream().mapToLong(DailySalesDTO::getOrdersCount).sum();

        long totalItems = orderRepository.findByCreatedOnBetween(fromDt, toDt).stream()
                .mapToLong(o -> {
                    Map<MenuItem, Integer> map = o.getMenuItemsWithQuantity();
                    return map == null ? 0 : map.values().stream().mapToInt(Integer::intValue).sum();
                }).sum();

        double avg = totalOrders > 0 ? round2(totalRevenue / totalOrders) : 0.0;

        SalesStatsDTO dto = new SalesStatsDTO();
        dto.setFrom(from);
        dto.setTo(to);
        dto.setDaily(daily);
        dto.setTotalRevenue(totalRevenue);
        dto.setTotalOrders(totalOrders);
        dto.setTotalItems(totalItems);
        dto.setAverageTicket(avg);
        return dto;
    }

    @Override
    public List<TopProductDTO> getTopProducts(LocalDate from, LocalDate to, int limit) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(23, 59, 59);
        return orderRepository.topProducts(fromDt, toDt, limit).stream()
                .map(r -> new TopProductDTO(
                        ((Number) r[0]).longValue(),
                        (String) r[1],
                        ((Number) r[2]).longValue(),
                        round2(((Number) r[3]).doubleValue())))
                .toList();
    }

    @Override
    public String exportCsv(LocalDate from, LocalDate to) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(23, 59, 59);
        List<Order> orders = orderRepository.findByCreatedOnBetween(fromDt, toDt);

        StringBuilder sb = new StringBuilder();
        sb.append("order_id,created_on,user_email,status,paid,items_count,total_cost\n");
        for (Order o : orders) {
            int itemsCount = 0;
            if (o.getMenuItemsWithQuantity() != null) {
                itemsCount = o.getMenuItemsWithQuantity().values().stream().mapToInt(Integer::intValue).sum();
            }
            sb.append(escape(o.getId())).append(',')
                    .append(o.getCreatedOn() != null ? o.getCreatedOn().format(DATE_FMT) : "").append(',')
                    .append(escape(o.getUser() != null ? o.getUser().getEmail() : "")).append(',')
                    .append(o.getStatus() != null ? o.getStatus().name() : "").append(',')
                    .append(Boolean.TRUE.equals(o.getPaid()) ? "1" : "0").append(',')
                    .append(itemsCount).append(',')
                    .append(o.getTotalCost() != null ? o.getTotalCost() : 0)
                    .append('\n');
        }
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
