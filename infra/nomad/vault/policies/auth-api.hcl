path "secret/data/auth-api" {
  capabilities = ["read"]
}

path "database/creds/auth-api" {
  capabilities = ["read"]
}

path "transit/sign/auth-api-jwt" {
  capabilities = ["update"]
}

path "transit/keys/auth-api-jwt" {
  capabilities = ["read"]
}
