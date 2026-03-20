package com.eventbus;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * A lightweight, thread-safe, async-capable in-process event bus.
 * Supports typed events, wildcard subscriptions, and async dispatch.
 */
public class EventBus {

    private static final Logger log = Logger.getLogger(EventBus.class.getName());

    private final Map<Class<?>, List<Consumer<Object>>> handlers = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final boolean asyncByDefault;

    public EventBus(boolean asyncByDefault, int threadPoolSize) {
        this.asyncByDefault = asyncByDefault;
        this.executor = asyncByDefault
            ? Executors.newFixedThreadPool(threadPoolSize)
            : null;
    }

    public static EventBus sync() {
        return new EventBus(false, 0);
    }

    public static EventBus async(int threads) {
        return new EventBus(true, threads);
    }

    // --- Subscribe ---

    @SuppressWarnings("unchecked")
    public <T> Subscription subscribe(Class<T> eventType, Consumer<T> handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add((Consumer<Object>) handler);
        log.fine(() -> "Subscribed to " + eventType.getSimpleName());
        return () -> unsubscribe(eventType, handler);
    }

    @SuppressWarnings("unchecked")
    private <T> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
        List<Consumer<Object>> list = handlers.get(eventType);
        if (list != null) list.remove(handler);
    }

    // --- Publish ---

    public void publish(Object event) {
        Objects.requireNonNull(event, "Event must not be null");
        Class<?> eventType = event.getClass();

        // Walk the class hierarchy to support base-type subscriptions
        Set<Class<?>> types = new LinkedHashSet<>();
        Class<?> cls = eventType;
        while (cls != null) {
            types.add(cls);
            cls = cls.getSuperclass();
        }
        types.addAll(Arrays.asList(eventType.getInterfaces()));

        int dispatchCount = 0;
        for (Class<?> type : types) {
            List<Consumer<Object>> list = handlers.get(type);
            if (list == null || list.isEmpty()) continue;
            for (Consumer<Object> handler : list) {
                dispatchCount++;
                if (asyncByDefault) {
                    executor.submit(() -> safeInvoke(handler, event));
                } else {
                    safeInvoke(handler, event);
                }
            }
        }
        log.fine(() -> "Published " + eventType.getSimpleName() + " to " + dispatchCount + " handler(s)");
    }

    private void safeInvoke(Consumer<Object> handler, Object event) {
        try {
            handler.accept(event);
        } catch (Exception e) {
            log.warning("Handler threw exception for event " + event.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

// --- Subscription handle (for easy unsubscription) ---

@FunctionalInterface
interface Subscription {
    void cancel();
}

// --- Example Events ---

record UserRegisteredEvent(String userId, String email, long timestamp) {}
record OrderPlacedEvent(String orderId, String userId, double total) {}
record PaymentFailedEvent(String orderId, String reason) {}

// --- Demo ---

class EventBusDemo {
    public static void main(String[] args) throws InterruptedException {

        EventBus bus = EventBus.async(4);

        // Email service subscribes to new users
        Subscription emailSub = bus.subscribe(UserRegisteredEvent.class, event -> {
            System.out.println("[EmailService] Sending welcome email to: " + event.email());
        });

        // Analytics subscribes to orders
        bus.subscribe(OrderPlacedEvent.class, event -> {
            System.out.printf("[Analytics] New order %s from user %s — $%.2f%n",
                event.orderId(), event.userId(), event.total());
        });

        // Alert service subscribes to payment failures
        bus.subscribe(PaymentFailedEvent.class, event -> {
            System.out.println("[Alerts] Payment failed for order " + event.orderId() + ": " + event.reason());
        });

        // Audit log subscribes to everything via Object
        bus.subscribe(Object.class, event -> {
            System.out.println("[AuditLog] Event recorded: " + event.getClass().getSimpleName());
        });

        System.out.println("--- Publishing events ---");
        bus.publish(new UserRegisteredEvent("u-001", "alice@example.com", System.currentTimeMillis()));
        bus.publish(new OrderPlacedEvent("o-001", "u-001", 99.99));
        bus.publish(new PaymentFailedEvent("o-002", "Insufficient funds"));

        // Unsubscribe email service
        emailSub.cancel();
        System.out.println("\n--- Email service unsubscribed ---");
        bus.publish(new UserRegisteredEvent("u-002", "bob@example.com", System.currentTimeMillis()));

        Thread.sleep(500); // let async handlers finish
        bus.shutdown();
        System.out.println("Done.");
    }
}
