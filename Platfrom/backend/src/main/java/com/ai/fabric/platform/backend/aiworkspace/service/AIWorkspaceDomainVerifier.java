package com.ai.fabric.platform.backend.aiworkspace.service;

import org.springframework.stereotype.Service;

import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;

@Service
public class AIWorkspaceDomainVerifier {

    public Verification verifyOwnershipAndRoute(String hostname, String token) {
        Verification ownership = verifyOwnership(hostname, token);
        return ownership.verified() ? verifyRoute(hostname) : ownership;
    }

    public Verification verifyOwnership(String hostname, String token) {
        String recordName = "_loomai-workspace." + hostname;
        try {
            Hashtable<String, String> environment = new Hashtable<>();
            environment.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext context = new InitialDirContext(environment);
            Attributes attributes = context.getAttributes(recordName, new String[]{"TXT"});
            var values = attributes.get("TXT");
            boolean ownership = false;
            if (values != null) {
                for (int index = 0; index < values.size(); index++) {
                    String value = String.valueOf(values.get(index)).replace("\"", "").trim();
                    if (("loomai-workspace-verification=" + token).equals(value)) {
                        ownership = true;
                        break;
                    }
                }
            }
            if (!ownership) {
                return new Verification(false, "The expected TXT ownership record is not visible yet.");
            }
            return new Verification(true, null);
        } catch (Exception ex) {
            return new Verification(false, "DNS ownership verification is not ready: " + ex.getClass().getSimpleName());
        }
    }

    public Verification verifyRoute(String hostname) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(hostname);
            if (addresses.length == 0 || java.util.Arrays.stream(addresses).anyMatch(address -> !isPublicAddress(address))) {
                return new Verification(false, "The hostname must resolve only to public addresses.");
            }
            for (InetAddress address : addresses) {
                if (!servesHealthyPinnedRoute(hostname, address)) {
                    return new Verification(false, "Every resolved address must serve a direct healthy TLS workspace route.");
                }
            }
            return new Verification(true, null);
        } catch (Exception ex) {
            return new Verification(false, "DNS or TLS verification is not ready: " + ex.getClass().getSimpleName());
        }
    }

    private boolean servesHealthyPinnedRoute(String hostname, InetAddress address) throws Exception {
        SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        try (Socket transport = new Socket()) {
            transport.connect(new InetSocketAddress(address, 443), 5_000);
            try (SSLSocket socket = (SSLSocket) socketFactory.createSocket(transport, hostname, 443, true)) {
                socket.setSoTimeout(8_000);
                SSLParameters parameters = socket.getSSLParameters();
                parameters.setEndpointIdentificationAlgorithm("HTTPS");
                parameters.setServerNames(java.util.List.of(new SNIHostName(hostname)));
                socket.setSSLParameters(parameters);
                socket.startHandshake();
                OutputStream output = socket.getOutputStream();
                output.write(("GET /health HTTP/1.1\r\n"
                    + "Host: " + hostname + "\r\n"
                    + "User-Agent: LoomAI-Workspace-Domain-Verifier/1.0\r\n"
                    + "Accept: application/json\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                output.flush();
                String statusLine = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)).readLine();
                if (statusLine == null || !statusLine.startsWith("HTTP/")) return false;
                String[] parts = statusLine.split(" ", 3);
                if (parts.length < 2) return false;
                int status = Integer.parseInt(parts[1]);
                return status >= 200 && status < 300;
            }
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            if (first == 0 || first == 10 || first == 127 || first >= 224) return false;
            if (first == 100 && second >= 64 && second <= 127) return false;
            if (first == 169 && second == 254) return false;
            if (first == 172 && second >= 16 && second <= 31) return false;
            if (first == 192 && second == 168) return false;
            if (first == 192 && second == 0 && (third == 0 || third == 2)) return false;
            if (first == 192 && second == 88 && third == 99) return false;
            if (first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100))) return false;
            return !(first == 203 && second == 0 && third == 113);
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        if ((first & 0xfe) == 0xfc) return false;
        return !(first == 0x20 && second == 0x01
            && Byte.toUnsignedInt(bytes[2]) == 0x0d && Byte.toUnsignedInt(bytes[3]) == 0xb8);
    }

    public record Verification(boolean verified, String message) {
    }
}
