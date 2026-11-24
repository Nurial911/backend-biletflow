package kz.edu.biletflow.backend.dtos;

import lombok.Data;

/**
 * Where every place of an event stands, in tickets (one seat or standing place = one ticket).
 * With a capacity the parts add up exactly:
 * capacity = sold + reserved + available + availableHidden + blocked + notOnSale.
 * <ul>
 *   <li>{@code sold}: tickets of PAID orders (free registrations included).</li>
 *   <li>{@code reserved}: tickets held by PENDING orders that haven't expired; they return to {@code available}
 *       when the order is cancelled or expires.</li>
 *   <li>{@code available}: still for sale in visible ticket types (quantity - sold - reserved). It ignores
 *       sales windows and paid-sales activation: it is stock, not what can be bought this minute.</li>
 *   <li>{@code availableHidden}: the same for hidden ticket types; buyers can't see them until they are unhidden.</li>
 *   <li>{@code blocked}: seats the organizer took off sale (assigned seating only, else 0).</li>
 *   <li>{@code notOnSale}: places no ticket type covers. Assigned seating: the unblocked seats and standing places of
 *       sections not linked to a ticket type. General admission: capacity above the ticket types' total quantity.
 *       Null without a capacity.</li>
 *   <li>{@code refunded}: tickets of REFUNDED orders, for information only: they are back on sale, so they are
 *       already counted in {@code available}, not in {@code sold}.</li>
 * </ul>
 */
@Data
public class OccupancyResponse {
    private Integer capacity; // null = no limit (legacy events without a capacity)
    private long sold;
    private long reserved;
    private long available;
    private long availableHidden;
    private long blocked;
    private Long notOnSale;
    private long refunded;
}
