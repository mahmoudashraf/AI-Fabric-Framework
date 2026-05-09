locals {
  network_zone_by_location = {
    ash  = "us-east"
    fsn1 = "eu-central"
    hel1 = "eu-central"
    hil  = "us-west"
    nbg1 = "eu-central"
    sin  = "ap-southeast"
  }

  common_labels = merge(
    {
      "loom.component" = "coolify"
      "loom.hostRole"  = "coolify-controller"
      "loom.managedBy" = "terraform"
      "loom.owner"     = "platform"
    },
    var.resource_labels
  )

  hosts = {
    staging = {
      name             = "coolify-staging-01"
      server_type      = var.staging_server_type
      private_ipv4     = cidrhost(var.network_subnet_cidr, 10)
      autoupdate       = var.staging_coolify_autoupdate
      volume_size_gb   = var.staging_volume_size_gb
      runtime_wildcard = "*.runtime-staging.${var.dns_zone_name}"
    }
    production = {
      name             = "coolify-prod-01"
      server_type      = var.production_server_type
      private_ipv4     = cidrhost(var.network_subnet_cidr, 20)
      autoupdate       = var.production_coolify_autoupdate
      volume_size_gb   = var.production_volume_size_gb
      runtime_wildcard = "*.runtime.${var.dns_zone_name}"
    }
  }

  public_source_ips = concat(["0.0.0.0/0"], ["::/0"])
}
