variable "location" {
  description = "Hetzner Cloud location for the first Coolify hosts. Use nbg1 or fsn1 unless project context requires another location."
  type        = string
  default     = "nbg1"

  validation {
    condition     = contains(["fsn1", "nbg1", "hel1", "ash", "hil", "sin"], var.location)
    error_message = "location must be one of fsn1, nbg1, hel1, ash, hil, or sin."
  }
}

variable "network_cidr" {
  description = "Private network CIDR for the Coolify host pool."
  type        = string
  default     = "10.44.0.0/16"
}

variable "network_subnet_cidr" {
  description = "Subnet CIDR allocated inside the private Coolify network."
  type        = string
  default     = "10.44.0.0/24"
}

variable "ssh_key_name" {
  description = "Name for the Hetzner Cloud SSH key resource."
  type        = string
  default     = "loom-coolify-operator"
}

variable "ssh_public_key" {
  description = "Operator SSH public key. Do not put private keys or tokens here."
  type        = string

  validation {
    condition     = can(regex("^(ssh-ed25519|ssh-rsa|ecdsa-sha2-nistp256) ", var.ssh_public_key))
    error_message = "ssh_public_key must be an OpenSSH public key."
  }
}

variable "ssh_allowed_cidrs" {
  description = "CIDR allowlist for SSH. Public internet CIDRs are rejected."
  type        = list(string)

  validation {
    condition = (
      length(var.ssh_allowed_cidrs) > 0
      && !contains(var.ssh_allowed_cidrs, "0.0.0.0/0")
      && !contains(var.ssh_allowed_cidrs, "::/0")
    )
    error_message = "ssh_allowed_cidrs must be non-empty and must not include 0.0.0.0/0 or ::/0."
  }
}

variable "dashboard_allowed_cidrs" {
  description = "Optional CIDR allowlist for initial Coolify dashboard/API ports. Leave empty to keep dashboard ports closed at the Hetzner firewall."
  type        = list(string)
  default     = []

  validation {
    condition = (
      !contains(var.dashboard_allowed_cidrs, "0.0.0.0/0")
      && !contains(var.dashboard_allowed_cidrs, "::/0")
    )
    error_message = "dashboard_allowed_cidrs must not include 0.0.0.0/0 or ::/0."
  }
}

variable "coolify_dashboard_ports" {
  description = "Initial Coolify dashboard/API ports to open only for dashboard_allowed_cidrs."
  type        = list(string)
  default     = ["8000"]
}

variable "staging_platform_api_allowed_cidrs" {
  description = "Optional staging-only CIDR allowlist for Platform control-plane access to the Coolify API port. Use a narrow egress CIDR when available; 0.0.0.0/0 is acceptable only as a temporary staging unblock when the provider has no stable egress IP."
  type        = list(string)
  default     = []
}

variable "production_platform_api_allowed_cidrs" {
  description = "Production-only CIDR allowlist for Platform control-plane access to the Coolify API port. Must be narrow; public internet CIDRs are rejected."
  type        = list(string)
  default     = []

  validation {
    condition = (
      !contains(var.production_platform_api_allowed_cidrs, "0.0.0.0/0")
      && !contains(var.production_platform_api_allowed_cidrs, "::/0")
    )
    error_message = "production_platform_api_allowed_cidrs must not include 0.0.0.0/0 or ::/0."
  }
}

variable "staging_server_type" {
  description = "Hetzner server type for staging."
  type        = string
  default     = "cpx32"
}

variable "production_server_type" {
  description = "Hetzner server type for production."
  type        = string
  default     = "ccx23"
}

variable "server_image" {
  description = "Hetzner image slug."
  type        = string
  default     = "ubuntu-24.04"
}

variable "enable_server_backups" {
  description = "Enable Hetzner server backups. This has a provider cost; Coolify data still needs an application-level backup plan."
  type        = bool
  default     = false
}

variable "enable_data_volumes" {
  description = "Create and attach optional Hetzner volumes for Coolify data. Mounting and restore policy must be verified before production workloads."
  type        = bool
  default     = false
}

variable "staging_volume_size_gb" {
  description = "Optional staging volume size in GB."
  type        = number
  default     = 80
}

variable "production_volume_size_gb" {
  description = "Optional production volume size in GB."
  type        = number
  default     = 160
}

variable "volume_delete_protection" {
  description = "Protect optional data volumes from accidental deletion."
  type        = bool
  default     = true
}

variable "admin_user" {
  description = "Non-root admin user created by cloud-init."
  type        = string
  default     = "loomops"
}

variable "install_coolify" {
  description = "Run the official Coolify installer during cloud-init."
  type        = bool
  default     = true
}

variable "coolify_install_url" {
  description = "Official Coolify install script URL."
  type        = string
  default     = "https://cdn.coollabs.io/coolify/install.sh"
}

variable "staging_coolify_autoupdate" {
  description = "Enable Coolify AUTOUPDATE during staging bootstrap."
  type        = bool
  default     = true
}

variable "production_coolify_autoupdate" {
  description = "Enable Coolify AUTOUPDATE during production bootstrap. Keep false unless an operator explicitly accepts the rollout risk."
  type        = bool
  default     = false

  validation {
    condition     = var.production_coolify_autoupdate == false
    error_message = "production_coolify_autoupdate must remain false for Slice 0."
  }
}

variable "docker_address_pool_base" {
  description = "Docker default address pool base passed to the Coolify installer."
  type        = string
  default     = "172.30.0.0/16"
}

variable "docker_address_pool_size" {
  description = "Docker address pool size passed to the Coolify installer."
  type        = number
  default     = 24
}

variable "dns_zone_name" {
  description = "Base DNS zone used for planned Coolify records. This module outputs record instructions; it does not manage DNS without provider credentials."
  type        = string
  default     = "loomai.pro"
}

variable "resource_labels" {
  description = "Additional labels merged into every Hetzner resource."
  type        = map(string)
  default     = {}
}
