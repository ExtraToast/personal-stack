path "secret/data/assistant-api" {
  capabilities = ["read"]
}
path "secret/data/assistant-api/*" {
  capabilities = ["read", "list"]
}
path "database/creds/assistant-api" {
  capabilities = ["read"]
}
path "transit/encrypt/assistant-api" {
  capabilities = ["update"]
}
path "transit/decrypt/assistant-api" {
  capabilities = ["update"]
}
path "pki/issue/assistant-api" {
  capabilities = ["create", "update"]
}
