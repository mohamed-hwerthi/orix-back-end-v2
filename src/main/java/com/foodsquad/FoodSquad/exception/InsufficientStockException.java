package com.foodsquad.FoodSquad.exception;

import java.util.List;

public class InsufficientStockException extends RuntimeException {

    private final List<Item> items;

    public InsufficientStockException(List<Item> items) {
        super(buildMessage(items));
        this.items = items;
    }

    public List<Item> getItems() {
        return items;
    }

    private static String buildMessage(List<Item> items) {
        StringBuilder sb = new StringBuilder("Stock insuffisant pour : ");
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            sb.append(it.title).append(" (demandé ").append(it.requested)
              .append(", dispo ").append(it.available).append(")");
            if (i < items.size() - 1) sb.append(", ");
        }
        return sb.toString();
    }

    public static class Item {
        public final Long menuItemId;
        public final String title;
        public final int requested;
        public final int available;

        public Item(Long menuItemId, String title, int requested, int available) {
            this.menuItemId = menuItemId;
            this.title = title;
            this.requested = requested;
            this.available = available;
        }
    }
}
