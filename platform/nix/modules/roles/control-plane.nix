{ ... }:
{
  services.k3s = {
    enable = true;
    role = "server";
    extraFlags = [
      "--disable=traefik"
      "--disable=servicelb"
      "--write-kubeconfig-mode=0644"
      # Trust OIDC tokens issued by auth-api so Headlamp can pass its
      # user's ID token straight through to the k8s API server. The
      # audience is the OAuth2 clientId registered in auth-api for
      # Headlamp; the groups claim is what drives per-user RBAC (see
      # platform/cluster/flux/apps/utility-system/headlamp/oidc-admin-binding.yaml).
      #
      # The oidc: prefix keeps these identities disjoint from the
      # built-in system:* groups so we never accidentally bind a
      # human-issued token to a system role.
      "--kube-apiserver-arg=oidc-issuer-url=https://auth.jorisjonkers.dev"
      "--kube-apiserver-arg=oidc-client-id=headlamp"
      "--kube-apiserver-arg=oidc-username-claim=preferred_username"
      "--kube-apiserver-arg=oidc-username-prefix=oidc:"
      "--kube-apiserver-arg=oidc-groups-claim=groups"
      "--kube-apiserver-arg=oidc-groups-prefix=oidc:"
    ];
  };
}
