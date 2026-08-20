package com.paris.mocap.actor;

import java.util.concurrent.atomic.AtomicInteger;

public final class EntityIdAllocator {
    private final AtomicInteger next;

    public EntityIdAllocator(int base) {
        this.next = new AtomicInteger(base);
    }

    public int nextId() {
        return this.next.getAndIncrement();
    }
}
