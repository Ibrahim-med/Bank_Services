package com.bankservices.bankservices_api.controller;

import com.bankservices.bankservices_api.repository.UtilisateurRepository;
import com.bankservices.bankservices_api.service.ClientSecurityService;
import com.bankservices.bankservices_api.service.CompteSecurityService;
import com.bankservices.bankservices_api.service.JwtService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Common infrastructure for the @WebMvcTest controller slices.
 *
 * Subclasses must add {@code @WebMvcTest(XController.class)} and
 * {@code @Import(SecurityConfig.class)} so the REAL security rules
 * (URL rules, @PreAuthorize, 401/403 handling) are applied.
 *
 * The JWT filter's collaborators and the ownership beans referenced in
 * @PreAuthorize SpEL ("clientSecurity", "compteSecurity") are mocked.
 * Users are supplied with @WithMockUser, so no real token is needed here
 * (real JWTs are covered by BankingApiIntegrationTest).
 */
@TestPropertySource(properties = "app.rate-limit.enabled=false") // covered by RateLimitIntegrationTest
abstract class AbstractControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected JwtService jwtService;

    @MockitoBean
    protected UtilisateurRepository utilisateurRepository;

    @MockitoBean(name = "clientSecurity")
    protected ClientSecurityService clientSecurity;

    @MockitoBean(name = "compteSecurity")
    protected CompteSecurityService compteSecurity;
}
