package com.odoomaster.ticketing.ticketing.internal;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A ticket's scannable code: 32 uppercase hexadecimal characters.
 *
 * <p>A <strong>generator</strong> value object. {@link #generate()} is the single definition of what
 * a QR code looks like — the shape used to be an inline expression in {@code TicketIssuanceImpl},
 * and the 160 QR tests in {@code ReliabilityMatrixTest} re-implemented it rather than calling it, so
 * they asserted the shape of a copy of the production code rather than the production code.
 *
 * <p><strong>Not persisted, and not used on the read path.</strong> {@code tickets.qr_code} stays a
 * {@code String} and {@code TicketRepository.findByQrCode} still takes one. Gate scanners hand us
 * whatever the camera decoded, so parsing that through a strict constructor would turn a smudged
 * barcode from a clean {@code 404 TICKET_NOT_FOUND} into a {@code 500}.
 *
 * @param value the 32-character uppercase hex code
 */
public record QrCode(String value) {

    /** 128 bits of UUID rendered as hex — collision-resistant enough for ticket identity. */
    private static final Pattern SHAPE = Pattern.compile("^[0-9A-F]{32}$");

    public QrCode {
        if (value == null || !SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("A QR code is 32 uppercase hex characters, was: " + value);
        }
    }

    /** A fresh, unique code for a newly issued ticket. */
    public static QrCode generate() {
        return new QrCode(UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return value;
    }
}
