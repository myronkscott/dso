/* 
 * The contents of this file are subject to the Terracotta Public License Version
 * 2.0 (the "License"); You may not use this file except in compliance with the
 * License. You may obtain a copy of the License at 
 *
 *      http://terracotta.org/legal/terracotta-public-license.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 *
 * The Covered Software is Terracotta Platform.
 *
 * The Initial Developer of the Covered Software is 
 *      Terracotta, Inc., a Software AG company
 */
package com.terracotta.management.security.impl;

import com.terracotta.management.keychain.URIKeyName;
import com.terracotta.management.security.HMACBuilder;
import com.terracotta.management.security.IACredentials;
import com.terracotta.management.security.InvalidIAInteractionException;
import com.terracotta.management.security.InvalidRequestTicketException;
import com.terracotta.management.security.KeyChainAccessor;
import com.terracotta.management.security.RequestIdentityAsserter;
import com.terracotta.management.security.RequestTicketMonitor;
import com.terracotta.management.security.UserService;
import com.terracotta.management.user.UserInfo;

import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author Ludovic Orban
 */
public final class TSAIdentityAsserter implements RequestIdentityAsserter {
  private static final String INVALID_IA_REQ = "Request received from host '%s' is missing the required IA parameters " +
      "to fulfill this request.";

  private final RequestTicketMonitor requestTicketMonitor;
  private final UserService userService;
  private final KeyChainAccessor keyChainAccessor;

  public TSAIdentityAsserter(RequestTicketMonitor requestTicketMonitor, UserService userService, KeyChainAccessor keyChainAccessor) {
    this.requestTicketMonitor = requestTicketMonitor;
    this.userService = userService;
    this.keyChainAccessor = keyChainAccessor;
  }

  @Override
  public UserInfo assertIdentity(HttpServletRequest request,
                                 HttpServletResponse response) throws InvalidIAInteractionException {
    String sessId = request.getHeader(IACredentials.TC_ID_TOKEN);
    String reqTicket = request.getHeader(IACredentials.REQ_TICKET);

    if (reqTicket == null || sessId == null) {
      throw new InvalidIAInteractionException(String.format(INVALID_IA_REQ, request.getRemoteAddr()));
    }

    try {
      requestTicketMonitor.redeemRequestTicket(reqTicket);
    } catch (InvalidRequestTicketException e) {
      throw new InvalidIAInteractionException(
          String.format("Request received from host '%s' presented an invalid request ticket.", request.getRemoteAddr()),
          e);
    }

    UserInfo user = userService.getUserInfo(sessId);
    if (user == null) {
      throw new InvalidIAInteractionException(
          String.format("Request received from host '%s' presented the currently invalid session id: '%s'", request.getRemoteAddr(), sessId));
    }

    String clientNonce = request.getHeader(IACredentials.CLIENT_NONCE);
    String alias = request.getHeader(IACredentials.ALIAS);

    if (clientNonce == null || alias == null) {
      throw new InvalidIAInteractionException(String.format(INVALID_IA_REQ, request.getRemoteAddr()));
    }

    try {
      URIKeyName uriAlias = new URIKeyName(alias);
      byte[] keyMaterial = keyChainAccessor.retrieveSecret(uriAlias);
      if (keyMaterial == null) {
        throw new RuntimeException("Missing keychain entry for URL [" + alias + "]");
      }
      response.setHeader("signature",
          HMACBuilder.getInstance(keyMaterial).addMessageComponent(reqTicket)
              .addMessageComponent(sessId).addMessageComponent(alias).addMessageComponent(clientNonce)
              .addUserDetail(user).buildEncoded());
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("BUG Alert! Failed to create signed hash.", e);
    } catch (InvalidKeyException e) {
      throw new RuntimeException("BUG Alert! Failed to create signed hash.", e);
    } catch (URISyntaxException e) {
      throw new RuntimeException(
          "BUG Alert! Unable to determine uri alias for obtaining the key material to sign the hash.", e);
    }

    return user;
  }
}
