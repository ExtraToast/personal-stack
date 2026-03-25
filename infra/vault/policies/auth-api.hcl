path "secret/data/auth-api/*" {
  capabilities = ["read", "list"]
}
path "database/creds/auth-api" {
  capabilities = ["read"]
}
path "transit/encrypt/auth-api" {
  capabilities = ["update"]
}
path "transit/decrypt/auth-api" {
  capabilities = ["update"]
}
path "pki/issue/auth-api" {
  capabilities = ["create", "update"]
}
