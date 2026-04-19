{ ... }:
{
  services.adguardhome = {
    enable = true;
    mutableSettings = false;
    host = "0.0.0.0";
    port = 3000;
  };
}
