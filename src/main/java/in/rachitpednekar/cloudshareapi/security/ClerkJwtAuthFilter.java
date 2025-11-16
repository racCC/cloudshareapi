package in.rachitpednekar.cloudshareapi.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class ClerkJwtAuthFilter extends OncePerRequestFilter {

    @Value("${clerk.issuer}")
    private String clerkIssuer;

    private final ClerkJwksProvider jwksProvider;


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {



        if ("OPTIONS".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        // For webhook endpoints, skip JWT validation and continue the filter chain
        if (request.getRequestURI().contains("/webhooks") ||
                request.getRequestURI().contains("/public") ||
                request.getRequestURI().contains("/download") ||
                request.getRequestURI().contains("/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        System.out.println("=== JWT AUTH DEBUG ===");
        System.out.println("Request URI: " + request.getRequestURI());
        System.out.println("Request Method: " + request.getMethod());

        String authHeader = request.getHeader("Authorization");
        System.out.println("Auth Header: " + (authHeader != null ? "Present" : "Missing"));

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            System.out.println("ERROR: Missing or invalid Authorization header");
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Authorization header missing/invalid");
            return;
        }

        try {
            String token = authHeader.substring(7);
            System.out.println("Token length: " + token.length());

            String[] chunks = token.split("\\.");
            if (chunks.length < 3) {
                System.out.println("ERROR: Invalid JWT format");
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid JWT token format");
                return;
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(chunks[0]));
            ObjectMapper mapper = new ObjectMapper();
            JsonNode headerNode = mapper.readTree(headerJson);

            if (!headerNode.has("kid")) {
                System.out.println("TOKEN ERROR: Missing kid");
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Token header is missing kid");
                return;
            }

            String kid = headerNode.get("kid").asText();
            System.out.println("Token kid: " + kid);

            PublicKey publicKey = jwksProvider.getPublicKey(kid);

            //verify the token
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .setAllowedClockSkewSeconds(60)
                    .requireIssuer(clerkIssuer)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String clerkId = claims.getSubject();
            System.out.println("JWT validation successful for user: " + clerkId);

            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(clerkId, null, Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            System.out.println("JWT validation failed: " + e.getMessage());
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid JWT token: " + e.getMessage());
            return;
        }
    }
}

