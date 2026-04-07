describe("Personal Access Tokens", () => {
  beforeEach(() => {
    cy.deleteAllPatsViaApi();
    cy.loginAndVisitPats();
  });

  it("shows empty state when no tokens exist", () => {
    cy.contains("No personal access tokens created yet.");
  });

  it("creates a PAT and shows token once", () => {
    cy.get("#pat-name").type("my-ci-token");
    cy.contains("Select roles").click();
    cy.contains("maven-read").click();
    cy.get("body").click(0, 0);

    cy.contains("Create token").click();

    cy.contains("Copy this token now").should("be.visible");
    cy.get('[aria-label="Personal access token"]').should("exist");
  });

  it("reveals the token", () => {
    cy.get("#pat-name").type("reveal-test");
    cy.contains("Select roles").click();
    cy.contains("maven-read").click();
    cy.get("body").click(0, 0);
    cy.contains("Create token").click();

    cy.get('[aria-label="Show token"]').click();
    cy.get('[aria-label="Personal access token"]')
      .invoke("val")
      .should("have.length", 64);
  });

  it("PAT appears in list with roles and expiry after creation", () => {
    const tomorrow = new Date(Date.now() + 2 * 24 * 60 * 60 * 1000)
      .toISOString()
      .split("T")[0];

    cy.get("#pat-name").type("list-test-token");
    cy.contains("Select roles").click();
    cy.contains("maven-read").click();
    cy.contains("maven-deploy").click();
    cy.get("body").click(0, 0);
    cy.get("#pat-expires").type(tomorrow);
    cy.contains("Create token").click();

    cy.contains("Done").click();

    cy.contains("list-test-token").should("be.visible");
    cy.contains("maven-read").should("be.visible");
    cy.contains("maven-deploy").should("be.visible");
  });

  it("deletes a PAT and removes it from the list", () => {
    cy.createPatViaApi("to-delete", ["maven-read"]);
    cy.reload();

    cy.contains("to-delete").should("be.visible");
    cy.contains("Delete").first().click();

    cy.contains(`Delete "to-delete"? This action cannot be undone.`);
    cy.get('[aria-label="Delete personal access token?"]')
      .contains("Delete")
      .click();

    cy.contains("to-delete").should("not.exist");
  });

  it("shows error for duplicate token name", () => {
    cy.createPatViaApi("existing-token", ["maven-read"]);
    cy.reload();

    cy.get("#pat-name").type("existing-token");
    cy.contains("Select roles").click();
    cy.contains("maven-read").click();
    cy.get("body").click(0, 0);
    cy.contains("Create token").click();

    cy.get("#pat-server-error").should("be.visible");
  });

  it("shows validation error when no name is given", () => {
    cy.contains("Select roles").click();
    cy.contains("maven-read").click();
    cy.get("body").click(0, 0);
    cy.contains("Create token").click();

    cy.contains("Name is required").should("be.visible");
  });

  it("shows validation error when no roles are selected", () => {
    cy.get("#pat-name").type("noroles-token");
    cy.contains("Create token").click();

    cy.contains("At least one role is required").should("be.visible");
  });

  describe("/auth endpoint", () => {
    it("returns 200 with identity headers for a valid token", () => {
      cy.createPatViaApi("auth-test", ["maven-read"]).then((token) => {
        const { keycloakUrl, realm, username } = Cypress.env();
        const basicAuth = btoa(`${username}:${token}`);

        cy.request({
          url: `${keycloakUrl}/realms/${realm}/personal-access-token/auth`,
          headers: { Authorization: `Basic ${basicAuth}` },
        }).then((resp) => {
          expect(resp.status).to.eq(200);
          expect(resp.headers["x-user"]).to.eq(username);
          expect(resp.headers["x-user-id"]).to.be.a("string");
          expect(resp.headers["x-roles"]).to.include("maven-read");
        });
      });
    });

    it("returns 403 when X-Required-Role is not in PAT", () => {
      cy.createPatViaApi("role-test", ["maven-read"]).then((token) => {
        const { keycloakUrl, realm, username } = Cypress.env();
        const basicAuth = btoa(`${username}:${token}`);

        cy.request({
          url: `${keycloakUrl}/realms/${realm}/personal-access-token/auth`,
          headers: {
            Authorization: `Basic ${basicAuth}`,
            "X-Required-Role": "docker-pull",
          },
          failOnStatusCode: false,
        }).its("status").should("eq", 403);
      });
    });

    it("returns 401 for wrong token", () => {
      const { keycloakUrl, realm, username } = Cypress.env();
      const wrongToken = "a".repeat(64);
      const basicAuth = btoa(`${username}:${wrongToken}`);

      cy.request({
        url: `${keycloakUrl}/realms/${realm}/personal-access-token/auth`,
        headers: { Authorization: `Basic ${basicAuth}` },
        failOnStatusCode: false,
      }).its("status").should("eq", 401);
    });
  });
});
