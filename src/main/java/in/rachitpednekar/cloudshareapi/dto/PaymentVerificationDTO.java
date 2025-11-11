package in.rachitpednekar.cloudshareapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor

public class PaymentVerificationDTO {
    private String stripe_order_id;
    private String stripe_payment_id;
    private String stripe_signature;
    private String sessionId;
    private String planId;




}
