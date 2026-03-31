CF_DNS_API_TOKEN={{ with secret "secret/data/platform/edge" }}{{ index .Data.data "cloudflare.dns_api_token" }}{{ end }}
