resource "hcloud_ssh_key" "operator" {
  name       = var.ssh_key_name
  public_key = var.ssh_public_key
  labels     = merge(local.common_labels, { "loom.environment" = "shared" })
}

resource "hcloud_network" "coolify" {
  name     = "loom-coolify-network"
  ip_range = var.network_cidr
  labels   = merge(local.common_labels, { "loom.environment" = "shared" })
}

resource "hcloud_network_subnet" "coolify" {
  network_id   = hcloud_network.coolify.id
  type         = "cloud"
  network_zone = local.network_zone_by_location[var.location]
  ip_range     = var.network_subnet_cidr
}

resource "hcloud_firewall" "coolify" {
  name   = "loom-coolify-firewall"
  labels = merge(local.common_labels, { "loom.environment" = "shared" })

  rule {
    direction  = "in"
    protocol   = "tcp"
    port       = "22"
    source_ips = var.ssh_allowed_cidrs
  }

  rule {
    direction  = "in"
    protocol   = "tcp"
    port       = "80"
    source_ips = local.public_source_ips
  }

  rule {
    direction  = "in"
    protocol   = "tcp"
    port       = "443"
    source_ips = local.public_source_ips
  }

  dynamic "rule" {
    for_each = length(var.dashboard_allowed_cidrs) > 0 ? toset(var.coolify_dashboard_ports) : toset([])

    content {
      direction  = "in"
      protocol   = "tcp"
      port       = rule.value
      source_ips = var.dashboard_allowed_cidrs
    }
  }

  rule {
    direction       = "out"
    protocol        = "tcp"
    port            = "any"
    destination_ips = local.public_source_ips
  }

  rule {
    direction       = "out"
    protocol        = "udp"
    port            = "any"
    destination_ips = local.public_source_ips
  }

  rule {
    direction       = "out"
    protocol        = "icmp"
    destination_ips = local.public_source_ips
  }
}

resource "hcloud_firewall" "coolify_staging_platform_api" {
  count = length(var.staging_platform_api_allowed_cidrs) > 0 ? 1 : 0

  name = "loom-coolify-staging-platform-api-firewall"
  labels = merge(
    local.common_labels,
    {
      "loom.environment" = "staging"
      "loom.purpose"     = "platform-api-access"
    }
  )

  dynamic "rule" {
    for_each = toset(var.coolify_dashboard_ports)

    content {
      direction  = "in"
      protocol   = "tcp"
      port       = rule.value
      source_ips = var.staging_platform_api_allowed_cidrs
    }
  }
}

resource "hcloud_firewall" "coolify_production_platform_api" {
  count = length(var.production_platform_api_allowed_cidrs) > 0 ? 1 : 0

  name = "loom-coolify-production-platform-api-firewall"
  labels = merge(
    local.common_labels,
    {
      "loom.environment" = "production"
      "loom.purpose"     = "platform-api-access"
    }
  )

  dynamic "rule" {
    for_each = toset(var.coolify_dashboard_ports)

    content {
      direction  = "in"
      protocol   = "tcp"
      port       = rule.value
      source_ips = var.production_platform_api_allowed_cidrs
    }
  }
}

resource "hcloud_server" "coolify" {
  for_each = local.hosts

  name        = each.value.name
  image       = var.server_image
  server_type = each.value.server_type
  location    = var.location
  backups     = var.enable_server_backups
  ssh_keys    = [hcloud_ssh_key.operator.id]
  firewall_ids = concat(
    [hcloud_firewall.coolify.id],
    each.key == "staging" && length(var.staging_platform_api_allowed_cidrs) > 0
    ? [hcloud_firewall.coolify_staging_platform_api[0].id]
    : [],
    each.key == "production" && length(var.production_platform_api_allowed_cidrs) > 0
    ? [hcloud_firewall.coolify_production_platform_api[0].id]
    : []
  )

  public_net {
    ipv4_enabled = true
    ipv6_enabled = true
  }

  network {
    network_id = hcloud_network.coolify.id
    ip         = each.value.private_ipv4
  }

  user_data = templatefile("${path.module}/cloud-init/coolify-host.yaml.tftpl", {
    admin_user = var.admin_user
    bootstrap_script = indent(6, templatefile("${path.module}/cloud-init/coolify-bootstrap.sh.tftpl", {
      bootstrap_log_path    = "/var/log/loom-coolify-bootstrap.log"
      bootstrap_status_path = "/var/lib/loom-coolify/bootstrap-status.json"
      admin_user            = var.admin_user
      coolify_autoupdate    = tostring(each.value.autoupdate)
      coolify_install_url   = var.coolify_install_url
      dashboard_allowed_cidrs = jsonencode(
        each.key == "staging"
        ? distinct(concat(var.dashboard_allowed_cidrs, var.staging_platform_api_allowed_cidrs))
        : each.key == "production"
        ? distinct(concat(var.dashboard_allowed_cidrs, var.production_platform_api_allowed_cidrs))
        : var.dashboard_allowed_cidrs
      )
      dashboard_ports          = jsonencode(var.coolify_dashboard_ports)
      docker_address_pool_base = var.docker_address_pool_base
      docker_address_pool_size = tostring(var.docker_address_pool_size)
      environment              = each.key
      host_name                = each.value.name
      install_coolify          = tostring(var.install_coolify)
      ssh_allowed_cidrs        = jsonencode(var.ssh_allowed_cidrs)
    }))
    host_name           = each.value.name
    ssh_authorized_keys = [var.ssh_public_key]
  })

  labels = merge(
    local.common_labels,
    {
      "loom.environment" = each.key
    }
  )

  lifecycle {
    ignore_changes = [
      network,
      public_net,
      ssh_keys,
      user_data,
    ]
  }

  depends_on = [hcloud_network_subnet.coolify]
}

resource "hcloud_volume" "coolify_data" {
  for_each = var.enable_data_volumes ? local.hosts : {}

  name              = "${each.value.name}-coolify-data"
  size              = each.value.volume_size_gb
  format            = "ext4"
  automount         = true
  server_id         = hcloud_server.coolify[each.key].id
  delete_protection = var.volume_delete_protection
  labels = merge(
    local.common_labels,
    {
      "loom.environment" = each.key
      "loom.volumeRole"  = "coolify-data"
    }
  )
}
