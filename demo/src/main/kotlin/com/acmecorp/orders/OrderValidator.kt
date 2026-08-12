package com.acmecorp.orders

/**
 * Validates and prices an incoming order before it's accepted by the
 * fulfillment pipeline. Demo class for Test Scaffold Companion's
 * Marketplace screenshot -- deliberately mirrors a real order-processing
 * domain (not a toy example) so the generated test skeleton reads as
 * something a real engineer would actually write.
 */
class OrderValidator(private val inventory: InventoryClient, private val pricing: PricingEngine) {

    fun validate(order: Order): ValidationResult {
        if (order.items.isEmpty()) return ValidationResult.rejected("Order has no line items")
        val unavailable = order.items.filterNot { inventory.isInStock(it.sku, it.quantity) }
        if (unavailable.isNotEmpty()) {
            return ValidationResult.rejected("Out of stock: ${unavailable.joinToString { it.sku }}")
        }
        return ValidationResult.accepted()
    }

    fun calculateTotal(order: Order): Double =
        order.items.sumOf { pricing.unitPrice(it.sku) * it.quantity }

    fun applyDiscount(order: Order, discountCode: String): Double {
        val base = calculateTotal(order)
        val discount = pricing.discountFor(discountCode)
        return base * (1 - discount)
    }

    fun isEligibleForFreeShipping(order: Order): Boolean =
        calculateTotal(order) >= FREE_SHIPPING_THRESHOLD

    companion object {
        private const val FREE_SHIPPING_THRESHOLD = 75.0
    }
}

data class Order(val id: String, val items: List<LineItem>)
data class LineItem(val sku: String, val quantity: Int)
data class ValidationResult(val accepted: Boolean, val reason: String?) {
    companion object {
        fun accepted() = ValidationResult(true, null)
        fun rejected(reason: String) = ValidationResult(false, reason)
    }
}

interface InventoryClient {
    fun isInStock(sku: String, quantity: Int): Boolean
}

interface PricingEngine {
    fun unitPrice(sku: String): Double
    fun discountFor(code: String): Double
}
