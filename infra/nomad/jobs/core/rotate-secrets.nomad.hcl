variable "repo_dir" {
  type    = string
  default = "/opt/personal-stack"
}

job "rotate-secrets" {
  datacenters = ["dc1"]
  type        = "batch"

  periodic {
    crons            = ["0 0 * * 0"]
    prohibit_overlap = true
    time_zone        = "Europe/Amsterdam"
  }

  group "rotate" {
    task "rotate" {
      driver = "raw_exec"

      config {
        command = "/bin/bash"
        args    = ["${var.repo_dir}/infra/scripts/setup.sh", "rotate-secrets"]
      }

      env {
        VAULT_ADDR      = "http://127.0.0.1:8200"
        VAULT_KEYS_FILE = "${var.repo_dir}/.vault-keys"
        DB_ENGINE_HOST  = "127.0.0.1"
        DB_ENGINE_PORT  = "5432"
      }

      resources {
        cpu    = 100
        memory = 128
      }
    }
  }
}
