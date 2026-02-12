import { describe, it, expect } from "vitest";
import { matchesSearch, getIconType } from "./schema-helpers";

// ─── matchesSearch ──────────────────────────────────────────

describe("matchesSearch", () => {
    it("returns no match for empty query", () => {
        const result = matchesSearch("Foo", "Some docs", "");
        expect(result).toEqual({ nameMatch: false, docMatch: false });
    });

    it("matches name case-insensitively", () => {
        const result = matchesSearch("AirShopping", "Request message", "airshopping");
        expect(result.nameMatch).toBe(true);
        expect(result.docMatch).toBe(false);
    });

    it("matches exact name", () => {
        const result = matchesSearch("Traveler", "", "Traveler");
        expect(result.nameMatch).toBe(true);
    });

    it("matches partial name", () => {
        const result = matchesSearch("AirShoppingRQ", "", "Shop");
        expect(result.nameMatch).toBe(true);
    });

    it("matches documentation when name does not match", () => {
        const result = matchesSearch("Foo", "This element contains airline data", "airline");
        expect(result.nameMatch).toBe(false);
        expect(result.docMatch).toBe(true);
    });

    it("prioritises name match over doc match", () => {
        const result = matchesSearch("airline", "airline info", "airline");
        expect(result.nameMatch).toBe(true);
        expect(result.docMatch).toBe(false);
    });

    it("returns no match when neither name nor doc match", () => {
        const result = matchesSearch("Foo", "Bar documentation", "xyz");
        expect(result).toEqual({ nameMatch: false, docMatch: false });
    });

    it("returns no doc match for empty documentation", () => {
        const result = matchesSearch("Foo", "", "Bar");
        expect(result).toEqual({ nameMatch: false, docMatch: false });
    });
});

// ─── getIconType ────────────────────────────────────────────

describe("getIconType", () => {
    it("returns 'message' for names ending with RQ", () => {
        expect(getIconType("AirShoppingRQ", null)).toBe("message");
    });

    it("returns 'message' for names ending with RS", () => {
        expect(getIconType("AirShoppingRS", "SomeType")).toBe("message");
    });

    it("returns 'typed' when typeName is present", () => {
        expect(getIconType("Traveler", "TravelerType")).toBe("typed");
    });

    it("returns 'element' when no typeName and not RQ/RS", () => {
        expect(getIconType("Anonymous", null)).toBe("element");
    });

    it("prioritises 'message' over 'typed' for RQ with type", () => {
        expect(getIconType("OrderCreateRQ", "OrderCreateRQType")).toBe("message");
    });
});
