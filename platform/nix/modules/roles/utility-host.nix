{ ... }:
{
  networking.firewall.allowedTCPPorts = [
    53
    80
    139
    443
    445
  ];
  networking.firewall.allowedUDPPorts = [
    53
    137
    138
  ];
}
