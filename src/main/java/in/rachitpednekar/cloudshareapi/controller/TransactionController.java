package in.rachitpednekar.cloudshareapi.controller;

import in.rachitpednekar.cloudshareapi.document.PaymentTransaction;
import in.rachitpednekar.cloudshareapi.document.ProfileDocument;
import in.rachitpednekar.cloudshareapi.repository.PaymentTransactionRepository;
import in.rachitpednekar.cloudshareapi.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final PaymentTransactionRepository paymentTransactionRepository;

    private final ProfileService profileService;
    @GetMapping
    public ResponseEntity<?> getUserTransactions() {
        ProfileDocument currentProfile=profileService.getCurrentProfile();
        String clerkId=currentProfile.getClerkId();
        List<PaymentTransaction> transactions=paymentTransactionRepository.findByClerkIdAndStatusOrderByTransactionDateDesc(clerkId,"SUCCESS");
        return ResponseEntity.ok(transactions);
    }
}
