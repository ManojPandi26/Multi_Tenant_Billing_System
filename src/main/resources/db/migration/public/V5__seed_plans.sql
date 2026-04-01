-- V5: Seed plans
INSERT INTO plans (
    name, display_name, description,
    price_monthly, price_annual, currency,
    trial_days, max_users, max_api_calls_per_month, max_storage_gb,
    is_active, is_public, created_at, updated_at, deleted, version
) VALUES
      (
          'FREE', 'Free Plan',
          'Get started with the basics. No credit card required.',
          0, 0, 'INR',
          0, 3, 1000, 1,
          TRUE, TRUE, NOW(), NOW(), FALSE, 0
      ),
      (
          'PRO', 'Pro Plan',
          'For growing teams that need more power and flexibility.',
          999, 9999, 'INR',
          14, 25, 50000, 20,
          TRUE, TRUE, NOW(), NOW(), FALSE, 0
      ),
      (
          'ENTERPRISE', 'Enterprise Plan',
          'Unlimited everything. Dedicated support. Custom SLAs.',
          4999, 49999, 'INR',
          30, -1, -1, 500,
          TRUE, TRUE, NOW(), NOW(), FALSE, 0
      )
    ON CONFLICT (name) DO NOTHING;