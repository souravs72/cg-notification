# Custom Domain Setup

## Quick Start

**With Route53 (automatic):**
```bash
export DOMAIN_NAME="notifications.example.com"
export ROUTE53_ZONE_ID="Z123456ABCDEFG"  # Get from Route53 console
./scripts/deploy-trigger.sh --domain=$DOMAIN_NAME --route53-zone-id=$ROUTE53_ZONE_ID
```

**Without Route53 (manual DNS):**
```bash
export DOMAIN_NAME="notifications.example.com"
./scripts/deploy-trigger.sh --domain=$DOMAIN_NAME
```

For non-Route53 DNS providers (Namecheap/Cloudflare/etc), Terraform will create the ACM certificate but you must add the DNS validation CNAMEs manually.

After the script runs, print the exact CNAMEs to add:

```bash
cd terraform
terraform output acm_certificate_validation_records
```

Add each record as a **CNAME** in your DNS provider:
- **Host**: use the left side (strip the trailing `.your-domain.com.`). If it’s for `www`, the host will include `.www` (example: `_abcd1234.www`).
- **Value**: the `...acm-validations.aws` target.

Wait for certificate validation (often 5–30 minutes), then re-run `deploy-trigger.sh` to enable HTTPS once ACM is `ISSUED`.

## Finding Route53 Zone ID

```bash
aws route53 list-hosted-zones --query 'HostedZones[*].[Name,Id]' --output table
```

## After Deployment

1. **Certificate validation**: Wait for ACM certificate to show `ISSUED` status (usually 5-30 minutes after DNS records are set).
2. **Point domain to ALB**: Add A/CNAME records pointing to the ALB DNS name (get with `terraform output alb_dns_name`).
3. **Verify**: `curl -I https://your-domain.com/actuator/health`

## Troubleshooting

- **Certificate stuck on PENDING_VALIDATION**: Verify DNS CNAME records are set correctly and wait longer (up to 30 minutes).
- **HTTPS not working**: Check certificate status is `ISSUED` and DNS has propagated (can take up to 48 hours, usually 5-60 minutes).

For detailed troubleshooting, see `terraform/DEPLOYMENT.md`.
