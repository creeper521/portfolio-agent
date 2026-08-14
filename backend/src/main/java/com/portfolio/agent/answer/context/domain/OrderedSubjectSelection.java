package com.portfolio.agent.answer.context.domain;

import com.portfolio.agent.answer.routing.domain.SubjectReference;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class OrderedSubjectSelection {
    private final SubjectOrderKind orderKind;
    private final List<Item> items;

    public OrderedSubjectSelection(SubjectOrderKind orderKind, List<Item> items) {
        this.orderKind = Objects.requireNonNull(orderKind, "orderKind");
        if (items == null || items.isEmpty() || items.size() > 5) throw new IllegalArgumentException("ordered selection size must be 1..5");
        List<Item> copy = items.stream().sorted(Comparator.comparingInt(Item::getPosition)).toList();
        for (int index = 0; index < copy.size(); index++) {
            if (copy.get(index).getPosition() != index + 1) throw new IllegalArgumentException("positions must be contiguous");
        }
        this.items = List.copyOf(copy);
    }
    public SubjectOrderKind getOrderKind() { return orderKind; }
    public List<Item> getItems() { return items; }

    public static final class Item {
        private final int position;
        private final SubjectReference subject;
        public Item(int position, SubjectReference subject) {
            if (position < 1) throw new IllegalArgumentException("position must be positive");
            this.position = position; this.subject = Objects.requireNonNull(subject, "subject");
        }
        public int getPosition() { return position; }
        public SubjectReference getSubject() { return subject; }
    }
}
