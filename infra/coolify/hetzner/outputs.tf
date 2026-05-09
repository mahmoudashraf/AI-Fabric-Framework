output "servers" {
  description = "Coolify host connection details. Public IPs are not secrets."
  value = {
    for environment, server in hcloud_server.coolify : environment => {
      name         = server.name
      server_type  = server.server_type
      location     = server.location
      public_ipv4  = server.ipv4_address
      public_ipv6  = server.ipv6_address
      private_ipv4 = local.hosts[environment].private_ipv4
      ssh_user     = var.admin_user
    }
  }
}

output "planned_dns_records" {
  description = "DNS records to create once DNS-provider credentials are available."
  value = [
    {
      environment = "production"
      type        = "A"
      name        = "coolify.ops.${var.dns_zone_name}"
      value       = try(hcloud_server.coolify["production"].ipv4_address, null)
      ttl         = 300
    },
    {
      environment = "production"
      type        = "AAAA"
      name        = "coolify.ops.${var.dns_zone_name}"
      value       = try(hcloud_server.coolify["production"].ipv6_address, null)
      ttl         = 300
    },
    {
      environment = "production"
      type        = "A"
      name        = "*.runtime.${var.dns_zone_name}"
      value       = try(hcloud_server.coolify["production"].ipv4_address, null)
      ttl         = 300
    },
    {
      environment = "production"
      type        = "AAAA"
      name        = "*.runtime.${var.dns_zone_name}"
      value       = try(hcloud_server.coolify["production"].ipv6_address, null)
      ttl         = 300
    },
    {
      environment = "staging"
      type        = "A"
      name        = "*.runtime-staging.${var.dns_zone_name}"
      value       = try(hcloud_server.coolify["staging"].ipv4_address, null)
      ttl         = 300
    },
    {
      environment = "staging"
      type        = "AAAA"
      name        = "*.runtime-staging.${var.dns_zone_name}"
      value       = try(hcloud_server.coolify["staging"].ipv6_address, null)
      ttl         = 300
    }
  ]
}

output "bootstrap_status_paths" {
  description = "Known bootstrap status paths to inspect after SSH."
  value = {
    for environment, host in local.hosts : environment => {
      host        = host.name
      log_path    = "/var/log/loom-coolify-bootstrap.log"
      status_path = "/var/lib/loom-coolify/bootstrap-status.json"
    }
  }
}

output "data_volumes" {
  description = "Optional Coolify data volumes, if enabled."
  value = {
    for environment, volume in hcloud_volume.coolify_data : environment => {
      id           = volume.id
      name         = volume.name
      size_gb      = volume.size
      linux_device = volume.linux_device
    }
  }
}
