import { describe, expect, it } from "vitest";
import { msg } from "./i18n";

describe("msg", () => {
  it("has required string keys", () => {
    expect(msg.pageTitle).toBeTruthy();
    expect(msg.noTokens).toBeTruthy();
    expect(msg.expired).toBeTruthy();
    expect(msg.expiringSoon).toBeTruthy();
    expect(msg.noExpiry).toBeTruthy();
  });

  it("deleteConfirm interpolates name", () => {
    expect(msg.deleteConfirm("my-token")).toContain("my-token");
  });
});
