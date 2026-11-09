package kz.edu.biletflow.backend.mappers;

import kz.edu.biletflow.backend.dtos.EventOrderResponse;
import kz.edu.biletflow.backend.dtos.OrderItemResponse;
import kz.edu.biletflow.backend.dtos.OrderResponse;
import kz.edu.biletflow.backend.dtos.PaymentResponse;
import kz.edu.biletflow.backend.entities.Order;
import kz.edu.biletflow.backend.entities.OrderItem;
import kz.edu.biletflow.backend.entities.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    @Mapping(target = "eventId", source = "event.id")
    @Mapping(target = "eventTitle", source = "event.title")
    @Mapping(target = "promoCode", source = "promoCampaign.code")
    @Mapping(target = "subtotalAmount", expression = "java(order.getTotalAmount().add(order.getDiscountAmount()))")
    OrderResponse toDto(Order order);

    // Buyer is filled by the service from a separate query (see OrderRepository.findBuyers).
    @Mapping(target = "promoCode", source = "promoCampaign.code")
    @Mapping(target = "buyerId", ignore = true)
    @Mapping(target = "buyerEmail", ignore = true)
    @Mapping(target = "ticketCount", ignore = true)
    EventOrderResponse toEventOrderDto(Order order);

    @Mapping(target = "ticketTypeId", source = "ticketType.id")
    @Mapping(target = "ticketTypeName", source = "ticketType.name")
    OrderItemResponse toDto(OrderItem item);

    PaymentResponse toDto(Payment payment);
}
