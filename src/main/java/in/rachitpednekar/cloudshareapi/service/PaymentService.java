package in.rachitpednekar.cloudshareapi.service;

import in.rachitpednekar.cloudshareapi.document.PaymentTransaction;
import in.rachitpednekar.cloudshareapi.document.ProfileDocument;
import in.rachitpednekar.cloudshareapi.dto.PaymentDTO;
import in.rachitpednekar.cloudshareapi.dto.PaymentVerificationDTO;
import in.rachitpednekar.cloudshareapi.repository.PaymentTransactionRepository;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.stripe.Stripe;
import java.time.LocalDateTime;

import com.stripe.model.PaymentIntent;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import java.util.Formatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final ProfileService profileService;
    private final UserCreditService userCreditService;
    private final PaymentTransactionRepository paymentTransactionRepository;

    @Value("${stripe.key.secret}")
    private String stripeKeySecret;

    public PaymentDTO createOrder(PaymentDTO paymentDTO) {
        try {
            Stripe.apiKey = stripeKeySecret;

            ProfileDocument currentProfile = profileService.getCurrentProfile();

            // Build Stripe Checkout Session params
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl("https://getcloudshare.vercel.app/subscription?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl("https://getcloudshare.vercel.app/subscription")
                    .setCustomerEmail(currentProfile.getEmail())
                    .addLineItem(
                            SessionCreateParams.LineItem.builder()
                                    .setQuantity(1L)
                                    .setPriceData(
                                            SessionCreateParams.LineItem.PriceData.builder()
                                                    .setCurrency(paymentDTO.getCurrency())
                                                    .setUnitAmount(Long.valueOf(paymentDTO.getAmount())) // amount in cents
                                                    .setProductData(
                                                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                    .setName(paymentDTO.getPlanId() + " Plan")
                                                                    .setDescription("Purchase " + paymentDTO.getCredits() + " credits")
                                                                    .build()
                                                    )
                                                    .build()
                                    )
                                    .build()
                    )
                    .build();

            // Create Stripe Checkout Session
            Session session = Session.create(params);

            // Save transaction as before...
            PaymentTransaction transaction = PaymentTransaction.builder()
                    .clerkId(currentProfile.getClerkId())
                    .orderId(session.getId())
                    .planId(paymentDTO.getPlanId())
                    .amount(paymentDTO.getAmount())
                    .currency(paymentDTO.getCurrency())
                    .status("PENDING")
                    .transactionDate(LocalDateTime.now())
                    .userEmail(currentProfile.getEmail())
                    .userName(currentProfile.getFirstName() + " " + currentProfile.getLastName())
                    .build();

            paymentTransactionRepository.save(transaction);

            // Return checkoutUrl for Stripe Checkout
            return PaymentDTO.builder()
                    .checkoutUrl(session.getUrl()) // <-- Return the URL, not just sessionId
                    .success(true)
                    .message("Stripe Checkout session created successfully.")
                    .build();

        } catch (Exception e) {
            log.error("Error creating Stripe Checkout session", e);
            return PaymentDTO.builder()
                    .success(false)
                    .message("An error occurred while creating your payment session.")
                    .build();
        }
    }
    public PaymentDTO verifyPayment(PaymentVerificationDTO request) {
        try {
            Stripe.apiKey = stripeKeySecret;
            ProfileDocument currentProfile = profileService.getCurrentProfile();
            String clerkId = currentProfile.getClerkId();

            // Fetch the Stripe Checkout Session using sessionId
            Session session = Session.retrieve(request.getSessionId());
            PaymentIntent paymentIntent = PaymentIntent.retrieve(session.getPaymentIntent());

            // Check payment status
            if ("succeeded".equals(paymentIntent.getStatus())) {
                // Determine credits based on planId
                int creditsToAdd = 0;
                String plan = "BASIC";
                switch (request.getPlanId()) {
                    case "premium":
                        creditsToAdd = 500;
                        plan = "PREMIUM";
                        break;
                    case "ultimate":
                        creditsToAdd = 5000;
                        plan = "ULTIMATE";
                        break;
                }
                if (creditsToAdd > 0) {
                    userCreditService.addCredits(clerkId, creditsToAdd, plan);
                    updateTransactionStatus(session.getId(), "SUCCESS", paymentIntent.getId(), creditsToAdd);
                    return PaymentDTO.builder()
                            .success(true)
                            .message("Payment verified and credits added successfully")
                            .credits(userCreditService.getUserCredits(clerkId).getCredits())
                            .build();
                } else {
                    updateTransactionStatus(session.getId(), "FAILED", paymentIntent.getId(), null);
                    return PaymentDTO.builder()
                            .success(false)
                            .message("Invalid plan selected")
                            .build();
                }
            } else {
                updateTransactionStatus(session.getId(), "FAILED", paymentIntent.getId(), null);
                return PaymentDTO.builder()
                        .success(false)
                        .message("Payment not successful")
                        .build();
            }
        } catch (Exception e) {
            return PaymentDTO.builder()
                    .success(false)
                    .message("Error verifying payment: " + e.getMessage())
                    .build();
        }
    }


    private void updateTransactionStatus(String stripeOrderId, String status, String stripePaymentId, Integer creditsToAdd) {
        paymentTransactionRepository.findAll().stream()
                .filter(t -> t.getOrderId() != null && t.getOrderId().equals(stripeOrderId))
                .findFirst()
                .map(transaction -> {
                    transaction.setStatus(status);
                    transaction.setPaymentId(stripePaymentId);
                    if (creditsToAdd != null) {
                        transaction.setCreditsAdded(creditsToAdd);
                    }
                    return paymentTransactionRepository.save(transaction);
                })
                .orElse(null);
    }

    /**
     * Generate HMAC SHA256 signature for payment verification
     */
    private String generateHmacSha256Signature(String data, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKey);

        byte[] hmacData = mac.doFinal(data.getBytes());

        return toHexString(hmacData);
    }

    private String toHexString(byte[] bytes) {
        Formatter formatter = new Formatter();
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }

}
