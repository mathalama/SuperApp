package dev.mathalama.identityservice.infrastructure.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import ua_parser.Client;
import ua_parser.Parser;

@Component
public class DeviceDetector {

    private final Parser uaParser = new Parser();

    public String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || "unknown".equalsIgnoreCase(xfHeader)) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    public String detectOs(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown OS";
        }
        Client client = uaParser.parse(userAgent);
        if (client.os == null || client.os.family == null || "Other".equalsIgnoreCase(client.os.family)) {
            return "Unknown OS";
        }
        return client.os.family + (client.os.major != null ? " " + client.os.major : "");
    }

    public String detectBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown Browser";
        }
        Client client = uaParser.parse(userAgent);
        if (client.userAgent == null || client.userAgent.family == null
                || "Other".equalsIgnoreCase(client.userAgent.family)) {
            return "Unknown Browser";
        }
        return client.userAgent.family + (client.userAgent.major != null ? " " + client.userAgent.major : "");
    }
}
