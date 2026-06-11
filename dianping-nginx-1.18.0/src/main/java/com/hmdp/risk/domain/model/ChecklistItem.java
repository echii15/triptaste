package com.hmdp.risk.domain.model;

import lombok.Data;

@Data
public class ChecklistItem {
    private String category;
    private String item;
    private String priority;

    public ChecklistItem() {
    }

    public ChecklistItem(String category, String item, String priority) {
        this.category = category;
        this.item = item;
        this.priority = priority;
    }
}
