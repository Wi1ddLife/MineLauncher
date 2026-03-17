package com.minelauncher.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minelauncher.config.LauncherConfig;
import com.minelauncher.utils.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class MicrosoftAuthService {

    private static final Logger logger = LoggerFactory.getLogger(MicrosoftAuthService.class);
    private static final Gson GSON = new Gson();

    // These three values are the ONLY combination that works together —
    // 00000000402b5328 is registered by Microsoft with EXACTLY this redirect URI.
    public static final String CLIENT_ID    = "00000000402b5328";
    public static final String REDIRECT_URI = "https://login.microsoftonline.com/common/oauth2/nativeclient";

    private static final String AUTH_URL    = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String TOKEN_URL   = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_URL     = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL    = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_AUTH_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE  = "https://api.minecraftservices.com/minecraft/profile";

    private static final long XSTS_NO_XBOX = 2148916233L;
    private static final long XSTS_CHILD   = 2148916238L;

    /**
     * Build the Microsoft login URL.
     * After login, Microsoft redirects to:
     *   https://login.live.com/oauth20_desktop.srf?code=M.xxxx
     * We intercept this in the WebView before the page loads.
     */
    public static String buildAuthUrl() {
        return AUTH_URL
                + "?client_id="     + CLIENT_ID
                + "&response_type=code"
                + "&redirect_uri="  + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                + "&scope="         + URLEncoder.encode("XboxLive.signin offline_access", StandardCharsets.UTF_8)
                
                + "&prompt=select_account";
    }

    /** Complete the full auth chain from an OAuth code */
    public AuthResult loginWithCode(String code) throws AuthException {
        try {
            logger.info("Exchanging code for MS token...");
            String msToken = exchangeCode(code);

            logger.info("Getting XBL token...");
            XblToken xbl = getXblToken(msToken);

            logger.info("Getting XSTS token...");
            String xsts = getXstsToken(xbl.token);

            logger.info("Getting Minecraft token...");
            String mcToken = getMinecraftToken(xsts, xbl.userHash);

            logger.info("Fetching profile...");
            MinecraftProfile profile = getProfile(mcToken);

            LauncherConfig.AuthData auth = LauncherConfig.getInstance().getData().auth;
            auth.accessToken = mcToken;
            auth.username    = profile.name;
            auth.uuid        = profile.id;
            auth.expiresAt   = System.currentTimeMillis() + 86400_000L;
            LauncherConfig.getInstance().save();

            logger.info("Logged in as {}", profile.name);
            return new AuthResult(profile.name, profile.id, mcToken, profile.getSkinUrl());

        } catch (IOException e) {
            throw new AuthException("Authentication failed: " + e.getMessage(), e);
        }
    }

    private String exchangeCode(String code) throws IOException {
        String body = "client_id="     + URLEncoder.encode(CLIENT_ID,    StandardCharsets.UTF_8)
                + "&code="             + URLEncoder.encode(code,          StandardCharsets.UTF_8)
                + "&grant_type=authorization_code"
                + "&redirect_uri="     + URLEncoder.encode(REDIRECT_URI,  StandardCharsets.UTF_8);

        String resp = HttpClient.postForm(TOKEN_URL, body);
        logger.debug("Token response: {}", resp);
        JsonObject json = JsonParser.parseString(resp).getAsJsonObject();

        if (json.has("error")) {
            throw new IOException(json.has("error_description")
                    ? json.get("error_description").getAsString()
                    : json.get("error").getAsString());
        }
        return json.get("access_token").getAsString();
    }

    private XblToken getXblToken(String msToken) throws IOException {
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName",   "user.auth.xboxlive.com");
        props.addProperty("RpsTicket",  "d=" + msToken);

        JsonObject body = new JsonObject();
        body.add("Properties",       props);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType",    "JWT");

        String resp = HttpClient.post(XBL_URL, GSON.toJson(body));
        JsonObject json = JsonParser.parseString(resp).getAsJsonObject();

        return new XblToken(
                json.get("Token").getAsString(),
                json.getAsJsonObject("DisplayClaims")
                        .getAsJsonArray("xui").get(0).getAsJsonObject()
                        .get("uhs").getAsString()
        );
    }

    private String getXstsToken(String xblToken) throws IOException {
        com.google.gson.JsonArray tokens = new com.google.gson.JsonArray();
        tokens.add(xblToken);

        JsonObject props = new JsonObject();
        props.addProperty("SandboxId", "RETAIL");
        props.add("UserTokens", tokens);

        JsonObject body = new JsonObject();
        body.add("Properties",       props);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType",    "JWT");

        String resp = HttpClient.post(XSTS_URL, GSON.toJson(body));
        JsonObject json = JsonParser.parseString(resp).getAsJsonObject();

        if (json.has("XErr")) {
            long err = json.get("XErr").getAsLong();
            String msg = (err == XSTS_NO_XBOX) ? "No Xbox account. Create one at xbox.com"
                       : (err == XSTS_CHILD)   ? "Child account — parental consent required"
                       :                         "XSTS error: " + err;
            throw new IOException(msg);
        }
        return json.get("Token").getAsString();
    }

    private String getMinecraftToken(String xsts, String userHash) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xsts);

        String resp = HttpClient.post(MC_AUTH_URL, GSON.toJson(body));
        JsonObject json = JsonParser.parseString(resp).getAsJsonObject();

        if (!json.has("access_token"))
            throw new IOException("No Minecraft access token returned");
        return json.get("access_token").getAsString();
    }

    public MinecraftProfile getProfile(String mcToken) throws IOException {
        String resp = HttpClient.get(MC_PROFILE, mcToken);
        JsonObject json = JsonParser.parseString(resp).getAsJsonObject();
        if (json.has("error"))
            throw new IOException("Profile: " + json.get("errorMessage").getAsString());
        return GSON.fromJson(json, MinecraftProfile.class);
    }

    public boolean isLoggedIn() {
        LauncherConfig.AuthData a = LauncherConfig.getInstance().getData().auth;
        return a.accessToken != null && !a.accessToken.isEmpty()
                && System.currentTimeMillis() < a.expiresAt;
    }

    public AuthResult getStoredAuth() {
        if (!isLoggedIn()) return null;
        LauncherConfig.AuthData a = LauncherConfig.getInstance().getData().auth;
        return new AuthResult(a.username, a.uuid, a.accessToken,
                "https://crafatar.com/avatars/" + a.uuid + "?size=64&overlay");
    }

    public void logout() {
        LauncherConfig.AuthData a = LauncherConfig.getInstance().getData().auth;
        a.accessToken = ""; a.refreshToken = "";
        a.username = ""; a.uuid = ""; a.expiresAt = 0;
        LauncherConfig.getInstance().save();
    }

    public record AuthResult(String username, String uuid, String accessToken, String skinUrl) {}

    static class XblToken {
        final String token, userHash;
        XblToken(String t, String u) { token = t; userHash = u; }
    }

    public static class MinecraftProfile {
        public String id, name;
        public Skin[] skins;
        public Cape[] capes;

        public String getSkinUrl() {
            if (skins != null)
                for (Skin s : skins)
                    if ("ACTIVE".equals(s.state)) return s.url;
            return "https://crafatar.com/avatars/" + id + "?size=64&overlay";
        }

        public static class Skin { public String id, state, url, textureKey, variant; }
        public static class Cape { public String id, state, url, alias; }
    }
}
