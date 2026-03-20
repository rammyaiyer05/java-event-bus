# java-event-bus

A lightweight, thread-safe **publish/subscribe event bus** for Java. Zero dependencies. Supports typed events, class hierarchy subscriptions, async dispatch, and clean unsubscription handles.

## Features

- ✅ Strongly-typed event subscriptions with generics
- ✅ Sync and async dispatch modes (thread pool)
- ✅ Class hierarchy support — subscribe to a base class, receive subclass events
- ✅ Cancellable subscriptions (no memory leaks)
- ✅ Safe handler invocation — one bad handler won't crash others
- ✅ Zero external dependencies

## Quick Start

```bash
git clone https://github.com/yourusername/java-event-bus
cd java-event-bus
./mvnw exec:java -Dexec.mainClass="com.eventbus.EventBusDemo"
```

## Usage

### 1. Define Events (Records or POJOs)

```java
record UserRegisteredEvent(String userId, String email, long timestamp) {}
record OrderPlacedEvent(String orderId, double total) {}
```

### 2. Create the Bus

```java
// Synchronous
EventBus bus = EventBus.sync();

// Async with 4 worker threads
EventBus bus = EventBus.async(4);
```

### 3. Subscribe

```java
Subscription sub = bus.subscribe(UserRegisteredEvent.class, event -> {
    sendWelcomeEmail(event.email());
});
```

### 4. Publish

```java
bus.publish(new UserRegisteredEvent("u-001", "alice@example.com", System.currentTimeMillis()));
```

### 5. Unsubscribe

```java
sub.cancel(); // clean unsubscription, no memory leaks
```

## Hierarchy Subscriptions

Subscribing to a parent type catches all subtype events:

```java
// Catches ALL events (every class extends Object)
bus.subscribe(Object.class, event -> auditLog.record(event));

// Catches all PaymentEvent subclasses
bus.subscribe(PaymentEvent.class, event -> alert(event));
```

## Example Output

```
--- Publishing events ---
[EmailService] Sending welcome email to: alice@example.com
[AuditLog] Event recorded: UserRegisteredEvent
[Analytics] New order o-001 from user u-001 — $99.99
[AuditLog] Event recorded: OrderPlacedEvent
[Alerts] Payment failed for order o-002: Insufficient funds

--- Email service unsubscribed ---
[AuditLog] Event recorded: UserRegisteredEvent
```

## Design Patterns Used

- **Observer** — decoupled publisher/subscriber
- **Command** — events as value objects
- **Functional interface** — `Subscription` as a cancel handle

## Tech Stack

- **Java** 21 (Records, functional interfaces)
- **Maven**
- No external dependencies

## License

MIT
