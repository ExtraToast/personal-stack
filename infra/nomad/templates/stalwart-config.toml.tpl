{{ $domain := env "DOMAIN" }}# Stalwart Mail Server Configuration (Nomad)

server.hostname = "mail.{{ $domain }}"

server.listener.smtp.bind = "[::]:25"
server.listener.smtp.protocol = "smtp"

server.listener.submission.bind = "[::]:587"
server.listener.submission.protocol = "smtp"
server.listener.submission.tls.starttls = true

server.listener.submissions.bind = "[::]:465"
server.listener.submissions.protocol = "smtp"
server.listener.submissions.tls.implicit = true

server.listener.imap.bind = "[::]:143"
server.listener.imap.protocol = "imap"

server.listener.imaptls.bind = "[::]:993"
server.listener.imaptls.protocol = "imap"
server.listener.imaptls.tls.implicit = true

server.listener.pop3.bind = "[::]:110"
server.listener.pop3.protocol = "pop3"

server.listener.pop3s.bind = "[::]:995"
server.listener.pop3s.protocol = "pop3"
server.listener.pop3s.tls.implicit = true

server.listener.sieve.bind = "[::]:4190"
server.listener.sieve.protocol = "managesieve"

server.listener.http.bind = "[::]:8080"
server.listener.http.protocol = "http"

acme."letsencrypt".directory = "https://acme-v02.api.letsencrypt.org/directory"
acme."letsencrypt".challenge = "dns-01"
acme."letsencrypt".contact = ["postmaster@{{ $domain }}"]
acme."letsencrypt".domains = ["mail.{{ $domain }}"]
acme."letsencrypt".cache = "/opt/stalwart/etc/acme"
acme."letsencrypt".renew-before = "30d"
acme."letsencrypt".default = true
acme."letsencrypt".dns.provider = "cloudflare"
acme."letsencrypt".dns.secret = "{{ with secret "secret/data/platform/edge" }}{{ index .Data.data "cloudflare.dns_api_token" }}{{ end }}"

storage.data = "rocksdb"
storage.fts = "rocksdb"
storage.blob = "rocksdb"
storage.lookup = "rocksdb"
storage.directory = "internal"

store.rocksdb.type = "rocksdb"
store.rocksdb.path = "/opt/stalwart/data"
store.rocksdb.compression = "lz4"

directory.internal.type = "internal"
directory.internal.store = "rocksdb"

authentication.fallback-admin.user = "{{ with secret "secret/data/platform/mail" }}{{ index .Data.data "stalwart.admin_user" }}{{ end }}"
authentication.fallback-admin.secret = "{{ with secret "secret/data/platform/mail" }}{{ index .Data.data "stalwart.admin_password" }}{{ end }}"

metrics.prometheus.enable = true

tracer.log.type = "log"
tracer.log.level = "info"
tracer.log.path = "/opt/stalwart/logs"
tracer.log.prefix = "stalwart.log"
tracer.log.rotate = "daily"
tracer.log.ansi = false
tracer.log.enable = true
