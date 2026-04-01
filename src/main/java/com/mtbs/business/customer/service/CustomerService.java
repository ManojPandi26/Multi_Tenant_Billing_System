package com.mtbs.business.customer.service;

import com.mtbs.business.customer.dto.CreateCustomerRequest;
import com.mtbs.business.customer.dto.CustomerResponse;
import com.mtbs.business.customer.dto.UpdateCustomerRequest;
import com.mtbs.business.customer.entity.Customer;
import com.mtbs.shared.exception.ResourceException;
import com.mtbs.business.customer.repository.CustomerRepository;
import com.mtbs.billing.gateway.PaymentGatewayPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Manages tenant customers — the people/businesses the tenant sends invoices to.
 *
 * TenantContext is already set by JwtAuthenticationFilter for every
 * authenticated request. No manual context wiring needed here.
 *
 * Razorpay customer sync:
 *   On create: if email/phone/name is present, we create the customer in Razorpay
 *   and store the ID. This ID is used later to generate Razorpay payment links.
 *   If Razorpay creation fails, we log and continue — the customer is still
 *   created locally. Razorpay sync is best-effort, not blocking.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final PaymentGatewayPort paymentGateway;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        log.info("Creating customer: name={}, email={}", request.getName(), request.getEmail());

        // Email uniqueness within this tenant schema
        if (StringUtils.hasText(request.getEmail())
                && customerRepository.existsByEmail(request.getEmail())) {
            throw ResourceException.alreadyExists("Customer", request.getEmail());
        }

        Customer customer = Customer.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .gstin(request.getGstin())
                .build();

        // Sync to Razorpay — best-effort, never blocks local creation
        String razorpayCustomerId = syncToRazorpay(customer);
        customer.setRazorpayCustomerId(razorpayCustomerId);

        Customer saved = customerRepository.save(customer);
        log.info("Customer created — id={}, razorpayId={}", saved.getId(), saved.getRazorpayCustomerId());
        return mapToResponse(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CustomerResponse getById(Long customerId) {
        return mapToResponse(findOrThrow(customerId));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(String search, Pageable pageable) {
        if (StringUtils.hasText(search)) {
            // Non-blank search — use keyword query (no null params in JPQL)
            return customerRepository.searchByKeyword(search.trim(), pageable)
                    .map(this::mapToResponse);
        }
        // No search — use plain findAll variant (no JPQL involved, no type inference)
        return customerRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::mapToResponse);
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public CustomerResponse update(Long customerId, UpdateCustomerRequest request) {
        Customer customer = findOrThrow(customerId);

        // Email uniqueness — exclude self
        if (StringUtils.hasText(request.getEmail())
                && !request.getEmail().equalsIgnoreCase(customer.getEmail())
                && customerRepository.existsByEmailAndIdNot(request.getEmail(), customerId)) {
            throw ResourceException.alreadyExists("Customer", request.getEmail());
        }

        if (StringUtils.hasText(request.getName()))    customer.setName(request.getName());
        if (request.getEmail()   != null)              customer.setEmail(request.getEmail());
        if (request.getPhone()   != null)              customer.setPhone(request.getPhone());
        if (request.getAddress() != null)              customer.setAddress(request.getAddress());
        if (request.getGstin()   != null)              customer.setGstin(request.getGstin());

        Customer saved = customerRepository.save(customer);
        log.info("Customer updated — id={}", saved.getId());
        return mapToResponse(saved);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(Long customerId) {
        Customer customer = findOrThrow(customerId);

        // Block deletion if this customer has any non-void invoices
        if (customerRepository.hasActiveInvoices(customerId)) {
            throw ResourceException.invalid(
                "Cannot delete customer with open or paid invoices. Void all invoices first.");
        }

        // @SQLDelete handles the soft delete via UPDATE
        customerRepository.delete(customer);
        log.info("Customer soft-deleted — id={}", customerId);
    }

    // ── Internal helper (used by BusinessInvoiceService) ─────────────────────

    /**
     * Fetches the customer entity directly — used by BusinessInvoiceService
     * to validate the customer exists before creating an invoice.
     */
    @Transactional(readOnly = true)
    public Customer getEntityById(Long customerId) {
        return findOrThrow(customerId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private Customer findOrThrow(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> ResourceException.notFound("Customer", id));
    }

    /**
     * Creates the customer in Razorpay and returns the customer ID.
     * Returns null silently if creation fails — Razorpay sync is non-blocking.
     */
    private String syncToRazorpay(Customer customer) {
        // Only sync if we have at least a name (Razorpay minimum requirement)
        if (!StringUtils.hasText(customer.getName())) {
            return null;
        }
        try {
            String id = paymentGateway.createCustomer(
                    customer.getEmail() != null ? customer.getEmail() : "",
                    customer.getName(),
                    customer.getPhone() != null ? customer.getPhone() : "");
            log.debug("Razorpay customer created — id={}", id);
            return id;
        } catch (Exception e) {
            log.warn("Razorpay customer sync failed (non-blocking): {}", e.getMessage());
            return null;
        }
    }

    // ── Response mapping ──────────────────────────────────────────────────────

    public CustomerResponse mapToResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .name(customer.getName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .address(customer.getAddress())
                .gstin(customer.getGstin())
                .razorpayCustomerId(customer.getRazorpayCustomerId())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
}