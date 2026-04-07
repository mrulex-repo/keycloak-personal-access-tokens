declare global {
  namespace Cypress {
    interface Chainable {
      loginAndVisitPats(): Chainable<void>;
      getBearerToken(): Chainable<string>;
      createPatViaApi(name: string, roles: string[], expires?: string): Chainable<string>;
      deleteAllPatsViaApi(): Chainable<void>;
    }
  }
}

Cypress.Commands.add("getBearerToken", () => {
  const { keycloakUrl, realm, clientId, username, password } = Cypress.env();
  return cy
    .request({
      method: "POST",
      url: `${keycloakUrl}/realms/${realm}/protocol/openid-connect/token`,
      form: true,
      body: {
        grant_type: "password",
        client_id: clientId,
        username,
        password,
      },
    })
    .its("body.access_token");
});

Cypress.Commands.add("createPatViaApi", (name, roles, expires) => {
  return cy.getBearerToken().then((token) =>
    cy
      .request({
        method: "POST",
        url: Cypress.env("patApiUrl"),
        headers: { Authorization: `Bearer ${token}` },
        body: { name, roles, expires },
      })
      .its("body.token"),
  );
});

Cypress.Commands.add("deleteAllPatsViaApi", () => {
  return cy.getBearerToken().then((token) => {
    const headers = { Authorization: `Bearer ${token}` };
    cy.request({ url: Cypress.env("patApiUrl"), headers }).then((resp) => {
      resp.body.forEach((pat: { id: string }) => {
        cy.request({
          method: "DELETE",
          url: Cypress.env("patApiUrl"),
          headers,
          body: { id: pat.id },
        });
      });
    });
  });
});

Cypress.Commands.add("loginAndVisitPats", () => {
  const { keycloakUrl, realm, clientId, username, password } = Cypress.env();
  cy.visit("/");
  cy.origin(keycloakUrl, { args: { realm, clientId, username, password } }, ({ realm, clientId, username, password }) => {
    cy.get("#username").type(username);
    cy.get("#password").type(password);
    cy.get('[type="submit"]').click();
  });
  cy.url().should("include", "/account");
  cy.visit("/#/personal-access-tokens");
});

export {};
