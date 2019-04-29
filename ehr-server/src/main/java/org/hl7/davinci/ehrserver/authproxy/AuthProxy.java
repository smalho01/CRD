package org.hl7.davinci.ehrserver.authproxy;

import org.hl7.davinci.ehrserver.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Objects;

@RestController
public class AuthProxy {

  static final Logger logger = LoggerFactory.getLogger(AuthProxy.class);

  @Autowired
  private PayloadDAOImpl payloadDAO;

  @GetMapping("/auth")
  public void getAuth(@RequestParam Map<String, String> reqParamValue, HttpServletResponse httpServletResponse, HttpServletRequest request) throws IOException {
    String params = _parseRedirect(reqParamValue, request);
    UriComponentsBuilder forwardUrl = UriComponentsBuilder.fromHttpUrl(Config.get("oauth_authorize"));
    String redirectUrl = forwardUrl.toUriString() + params;
    logger.info("redirectUrl: " + redirectUrl);
    httpServletResponse.setHeader("Location", redirectUrl);

    httpServletResponse.setStatus(302);
  }

  @PostMapping(value = "/token",  consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
  public ResponseEntity<TokenResponse> getToken(TokenRequest body) {

    Payload payload = payloadDAO.findContextByCode(body.getCode());
    // should cross reference the supplied redirect uri with the one keycloak is expecting
    // AKA: Build the correct redirect uri instead of pulling it from the DB, so that keycloak
    // can verify that the secondary redirect uri, within the proxied redirect, is the same as
    // the original.  You could, potentially, swap the redirect url in this request if the
    // launch id is the same.
    //            - this has been a keeyan exclusive comment
    body.setRedirect_uri(payload.getRedirectUri());
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body.urlEncode(), httpHeaders);

    RestTemplate restTemplate = new RestTemplate();
    try {
      ResponseEntity<TokenResponse> response = restTemplate.postForEntity(Config.get("oauth_token"), request, TokenResponse.class);
      Objects.requireNonNull(response.getBody())
          .setPatient(payload.getPatientId())
          .setRequest(payload.getRequest())
          .setTemplate(payload.getTemplate());
      response = ResponseEntity.ok(response.getBody());

      return response;

    }catch (HttpClientErrorException e) {
      e.printStackTrace();
      System.out.println(e.getResponseBodyAsString());
      System.out.println(e.getResponseHeaders());
      ResponseEntity response = ResponseEntity.badRequest().body(String.class);
      return response;
    }

  }

  @PostMapping(value = "/_services/smart/Launch", consumes = {"application/json"})
  @ResponseBody
  public AuthResponse getLaunch(@RequestBody Payload payload) {
    AuthResponse authResponse = new AuthResponse();
    payload.setLaunchId(authResponse.getLaunchId());
    payloadDAO.createPayload(payload);
    return authResponse;

  }

  @GetMapping("/_auth/{launch}")
  public void authSync(@PathVariable String launch, @RequestParam Map<String, String> reqParamValue, HttpServletResponse httpServletResponse, HttpServletRequest request) {
    logger.info("redirected to secondary auth endpoint to sync code/launch_id with each other");
    payloadDAO.updateCode(launch, reqParamValue.get("code"));
    UriComponentsBuilder forwardUrl = UriComponentsBuilder.fromHttpUrl(reqParamValue.get("redirect_uri")).queryParam("state", reqParamValue.get("state")).queryParam("code", reqParamValue.get("code"));
    httpServletResponse.setHeader("Location", forwardUrl.toUriString());
    httpServletResponse.setStatus(302);

  }

  private String _parseRedirect(Map<String, String> reqParamValue, HttpServletRequest request) {
    String currentRedirectURI = reqParamValue.get("redirect_uri");
    String finalRedirectURI = "http://" +request.getLocalName() + ":" + request.getLocalPort() + request.getContextPath() + "/_auth/" + reqParamValue.get("launch") + "?redirect_uri=" + currentRedirectURI;
    reqParamValue.put("redirect_uri", finalRedirectURI);
    payloadDAO.updateRedirect(reqParamValue.get("launch"), finalRedirectURI);
    return paramFormatter(reqParamValue);
  }

  static String urlEncodeUTF8(Map<?,?> map) {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<?,?> entry : map.entrySet()) {
      if (sb.length() > 0) {
        sb.append("&");
      }
      sb.append(String.format("%s=%s",
          urlEncodeUTF8(entry.getKey().toString()),
          urlEncodeUTF8(entry.getValue().toString())
      ));
    }
    return sb.toString();
  }

  static String urlEncodeUTF8(String s) {
    try {
      return URLEncoder.encode(s, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new UnsupportedOperationException(e);
    }
  }

  private String paramFormatter(Map<String, String> params) {
    return params.entrySet().stream()
        .map(p -> urlEncodeUTF8(p.getKey()) + "=" + urlEncodeUTF8(p.getValue()))
        .reduce((p1, p2) -> p1 + "&" + p2).map(s -> "?" + s)
        .orElse("");
  }

}

