import { safeNext } from "./navigation";

describe("the post-login redirect", () => {
  it("allows a page on this site", () => {
    expect(safeNext("/book/1?service=2")).toBe("/book/1?service=2");
  });
  it.each(["https://evil.example", "//evil.example", "/\\evil.example", "javascript:alert(1)", ""])("refuses %s", (value) => {
    expect(safeNext(value)).toBeNull();
  });
  it("handles a missing value", () => {
    expect(safeNext(null)).toBeNull();
  });
});
