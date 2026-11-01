package io.github.yozh150991.todo.core.model

/**
 * Fractional ranking keys for `child_order` / `day_order` (spec §4.1).
 *
 * Port of the "fractional indexing" scheme (D. Greenspan / rocicorp): a key is a
 * variable-length integer part followed by an optional fraction, all over a base-62
 * alphabet ordered by plain string comparison. A reorder rewrites exactly one row and
 * repeated appends/prepends grow keys only logarithmically.
 */
object OrderKeys {
    private const val DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
    private val ZERO = DIGITS[0]
    private val SMALLEST_INTEGER = "A" + ZERO.toString().repeat(26)

    /** Key for the first element of an empty list. */
    fun first(): String = between(null, null)

    /** Key that sorts after [last] (or [first] when the list is empty). */
    fun after(last: String?): String = between(last, null)

    /** Key that sorts before [first] (or the initial key when the list is empty). */
    fun before(first: String?): String = between(null, first)

    fun isValid(key: String): Boolean = runCatching { validate(key) }.isSuccess

    /**
     * Returns a key strictly between [lower] and [upper]; `null` means an open bound.
     * Throws [IllegalArgumentException] if `lower >= upper` or a key is malformed.
     */
    fun between(lower: String?, upper: String?): String {
        lower?.let(::validate)
        upper?.let(::validate)
        if (lower != null && upper != null) {
            require(lower < upper) { "Lower key '$lower' must sort before upper key '$upper'" }
        }
        if (lower == null) {
            if (upper == null) return "a$ZERO"
            val intB = integerPart(upper)
            val fracB = upper.substring(intB.length)
            if (intB == SMALLEST_INTEGER) return intB + midpoint("", fracB)
            if (intB < upper) return intB
            return requireNotNull(decrementInteger(intB)) { "Cannot create a key before '$upper'" }
        }
        if (upper == null) {
            val intA = integerPart(lower)
            val fracA = lower.substring(intA.length)
            return incrementInteger(intA) ?: (intA + midpoint(fracA, null))
        }
        val intA = integerPart(lower)
        val fracA = lower.substring(intA.length)
        val intB = integerPart(upper)
        val fracB = upper.substring(intB.length)
        if (intA == intB) return intA + midpoint(fracA, fracB)
        val next = requireNotNull(incrementInteger(intA)) { "Cannot create a key after '$lower'" }
        return if (next < upper) next else intA + midpoint(fracA, null)
    }

    private fun digit(c: Char): Int {
        val d = DIGITS.indexOf(c)
        require(d >= 0) { "Invalid order key digit '$c'" }
        return d
    }

    /** [a] is a fraction lower bound (possibly empty), [b] an exclusive upper bound or null for +infinity. */
    private fun midpoint(a: String, b: String?): String {
        if (b != null) {
            var n = 0
            while (n < b.length && (if (n < a.length) a[n] else ZERO) == b[n]) n++
            if (n > 0) {
                return b.substring(0, n) + midpoint(if (n < a.length) a.substring(n) else "", b.substring(n))
            }
        }
        val digitA = if (a.isNotEmpty()) digit(a[0]) else 0
        val digitB = if (b != null) digit(b[0]) else DIGITS.length
        if (digitB - digitA > 1) {
            return DIGITS[(digitA + digitB + 1) / 2].toString()
        }
        return if (b != null && b.length > 1) {
            b.substring(0, 1)
        } else {
            DIGITS[digitA] + midpoint(if (a.isNotEmpty()) a.substring(1) else "", null)
        }
    }

    private fun integerLength(head: Char): Int = when (head) {
        in 'a'..'z' -> head - 'a' + 2
        in 'A'..'Z' -> 'Z' - head + 2
        else -> throw IllegalArgumentException("Invalid order key head '$head'")
    }

    private fun integerPart(key: String): String {
        require(key.isNotEmpty()) { "Order key must not be empty" }
        val length = integerLength(key[0])
        require(length <= key.length) { "Malformed order key: '$key'" }
        return key.substring(0, length)
    }

    private fun validate(key: String) {
        require(key != SMALLEST_INTEGER) { "Order key '$key' is reserved" }
        val intPart = integerPart(key)
        intPart.drop(1).forEach(::digit)
        val frac = key.substring(intPart.length)
        frac.forEach(::digit)
        require(!frac.endsWith(ZERO)) { "Order key '$key' must not end with '$ZERO'" }
    }

    private fun incrementInteger(x: String): String? {
        val head = x[0]
        val digits = x.substring(1).toCharArray()
        var carry = true
        var i = digits.size - 1
        while (carry && i >= 0) {
            val d = digit(digits[i]) + 1
            if (d == DIGITS.length) {
                digits[i] = ZERO
            } else {
                digits[i] = DIGITS[d]
                carry = false
            }
            i--
        }
        if (!carry) return head + digits.concatToString()
        if (head == 'Z') return "a$ZERO"
        if (head == 'z') return null
        val nextHead = head + 1
        val tail = digits.concatToString()
        return nextHead + (if (nextHead > 'a') tail + ZERO else tail.dropLast(1))
    }

    private fun decrementInteger(x: String): String? {
        val head = x[0]
        val digits = x.substring(1).toCharArray()
        var borrow = true
        var i = digits.size - 1
        while (borrow && i >= 0) {
            val d = digit(digits[i]) - 1
            if (d == -1) {
                digits[i] = DIGITS.last()
            } else {
                digits[i] = DIGITS[d]
                borrow = false
            }
            i--
        }
        if (!borrow) return head + digits.concatToString()
        if (head == 'a') return "Z" + DIGITS.last()
        if (head == 'A') return null
        val nextHead = head - 1
        val tail = digits.concatToString()
        return nextHead + (if (nextHead < 'Z') tail + DIGITS.last() else tail.dropLast(1))
    }
}
