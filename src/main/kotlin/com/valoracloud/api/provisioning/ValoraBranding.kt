package com.valoracloud.api.provisioning

/**
 * The customer-facing branding installed on every server (banner + MOTD).
 *
 * WHITE-LABEL RULE: customers must never see the upstream provider (Contabo).
 * These strings — and the de-branding commands in
 * ProvisioningProcessor.buildPostProvisionCommands — are guarded by
 * ProvisioningBrandingTest.
 */
object ValoraBranding {

    fun bannerContent(): String {
        return """██╗   ██╗ █████╗ ██╗      ██████╗ ██████╗  █████╗
██║   ██║██╔══██╗██║     ██╔═══██╗██╔══██╗██╔══██╗
██║   ██║███████║██║     ██║   ██║██████╔╝███████║   C L O U D
╚██╗ ██╔╝██╔══██║██║     ██║   ██║██╔══██╗██╔══██║
 ╚████╔╝ ██║  ██║███████╗╚██████╔╝██║  ██║██║  ██║
  ╚═══╝  ╚═╝  ╚═╝╚══════╝ ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝"""
    }

    fun motdScript(): String {
        val D = "$"
        val QUOTE = "'"
        return """#!/usr/bin/env bash
cream=${D}${QUOTE}\033[38;2;243;236;223m${QUOTE}; white=${D}${QUOTE}\033[38;2;232;238;247m${QUOTE}
brick=${D}${QUOTE}\033[38;2;224;99;94m${QUOTE};  blue=${D}${QUOTE}\033[38;2;122;162;255m${QUOTE}
green=${D}${QUOTE}\033[38;2;134;239;172m${QUOTE}; mute=${D}${QUOTE}\033[38;2;132;144;182m${QUOTE}
b=${D}${QUOTE}\033[1m${QUOTE}; r=${D}${QUOTE}\033[0m${QUOTE}
rule="  ${D}{mute}╶───────────────────────────────────────────────────────────────╴${D}{r}"
host=${D}(hostname -s)
fqdn=${D}([ -f /etc/valora/meta ] && grep -m1 '^fqdn=' /etc/valora/meta | cut -d= -f2 || hostname -f 2>/dev/null || hostname)
region_label=${D}([ -f /etc/valora/meta ] && grep -m1 '^region_label=' /etc/valora/meta | cut -d= -f2 || echo '—')
ip4=${D}(hostname -I 2>/dev/null | awk '{print ${D}1}')
ip6=${D}(ip -6 addr show scope global 2>/dev/null | awk '/inet6/{print ${D}2; exit}')
os=${D}(. /etc/os-release 2>/dev/null; echo "${D}{PRETTY_NAME:-Linux}")
cores=${D}(nproc 2>/dev/null || echo "?")
mem=${D}(free | awk '/Mem:/{printf "%d%%", ${D}3/${D}2*100}')
disk=${D}(df -h / | awk 'NR==2{print ${D}5" of "${D}2}')
load=${D}(cut -d" " -f1-3 /proc/loadavg); up=${D}(uptime -p | sed "s/^up //")
upd=${D}(/usr/lib/update-notifier/apt-check --human-readable 2>/dev/null | head -1)
printf '\n%s\n\n' "${D}rule"
while IFS= read -r l; do line=${D}{l/C L O U D/${D}{brick}C L O U D${D}{cream}}; printf '%s%s%s\n' "${D}b${D}cream" "${D}line" "${D}r"; done < /etc/valora/banner.txt
printf '\n   %sInfrastructure that picks up the phone.%s\n%s\n\n' "${D}mute" "${D}r" "${D}rule"
printf '   %sHOST   %s %s%s%s · %s\n' "${D}mute" "${D}r" "${D}white" "${D}host" "${D}r" "${D}fqdn"
printf '   %sREGION %s %-38s %sSTATUS%s %s● operational%s\n' "${D}mute" "${D}r" "${D}region_label" "${D}mute" "${D}r" "${D}green" "${D}r"
printf '   %sIPv4   %s %s%s%s              %sUPTIME%s %s\n' "${D}mute" "${D}r" "${D}white" "${D}ip4" "${D}r" "${D}mute" "${D}r" "${D}up"
printf '   %sIPv6   %s %s        %sLOAD  %s %s\n\n%s\n\n' "${D}mute" "${D}r" "${D}{ip6:---}" "${D}mute" "${D}r" "${D}load" "${D}rule"
printf '   %sSYSTEM %s %s · %s vCPU\n' "${D}mute" "${D}r" "${D}os" "${D}cores"
printf '   %sDISK   %s %s    %sMEM%s %s\n' "${D}mute" "${D}r" "${D}disk" "${D}mute" "${D}r" "${D}mem"
printf '   %sUPDATES%s %s%s%s\n\n%s\n\n' "${D}mute" "${D}r" "${D}green" "${D}{upd:-0 updates}" "${D}r" "${D}rule"
printf '   %spanel  %s %smy.valoracloud.com%s     %sdocs   %s %sdocs.valoracloud.com%s\n' "${D}mute" "${D}r" "${D}blue" "${D}r" "${D}mute" "${D}r" "${D}blue" "${D}r"
printf '   %sstatus %s %sstatus.valoracloud.com%s %ssupport%s %ssupport@valoracloud.com%s · 24/7\n\n%s\n\n' "${D}mute" "${D}r" "${D}blue" "${D}r" "${D}mute" "${D}r" "${D}blue" "${D}r" "${D}rule"
"""
    }
}
